"""RF-COM-012 (inmutabilidad y corrección por anulación) y RF-COM-013 (listados con filtros)."""

import uuid

import httpx

from tests import fabricas


async def _sesion(cliente: httpx.AsyncClient) -> dict:
    cuerpo = {
        "email": fabricas.correo_unico(),
        "password": fabricas.CONTRASENA_VALIDA,
        "nombre": "Marta",
        "negocio": {"nombre": "P", "moneda_base": "COP", "zona_horaria": "UTC"},
    }
    r = await cliente.post("/api/v1/auth/registro", json=cuerpo)
    return {"Authorization": f"Bearer {r.json()['token_acceso']}"}


def _clave() -> dict[str, str]:
    return {"Idempotency-Key": str(uuid.uuid4())}


async def _proveedor(cliente: httpx.AsyncClient, auth: dict, nombre: str = "P") -> dict:
    return (await cliente.post("/api/v1/proveedores", json={"nombre": nombre}, headers=auth)).json()


async def _producto(cliente: httpx.AsyncClient, auth: dict, nombre: str = "Cuaderno") -> dict:
    return (
        await cliente.post(
            "/api/v1/productos", json={"nombre": nombre, "unidad_codigo": "unidad"}, headers=auth
        )
    ).json()


async def _recepcion_confirmada(
    cliente: httpx.AsyncClient,
    auth: dict,
    prov: dict,
    productos: list[dict],
    fecha: str = "2026-09-02",
) -> dict:
    rc = await cliente.post(
        "/api/v1/recepciones",
        json={
            "proveedor_id": prov["id"],
            "fecha": fecha,
            "lineas": [
                {
                    "producto_id": p["id"],
                    "cantidad": "5",
                    "costo_unitario": {"monto": "10", "moneda": "COP"},
                }
                for p in productos
            ],
        },
        headers=auth,
    )
    assert rc.status_code == 201, rc.text
    r = await cliente.post(
        f"/api/v1/recepciones/{rc.json()['id']}/confirmar", json={}, headers=auth | _clave()
    )
    assert r.status_code == 200, r.text
    return r.json()


async def test_rf_com_012_anular_un_movimiento_de_la_recepcion_la_marca_corregida_sin_borrarla(
    cliente: httpx.AsyncClient,
) -> None:
    auth = await _sesion(cliente)
    prov = await _proveedor(cliente, auth)
    p1, p2 = await _producto(cliente, auth), await _producto(cliente, auth, "Lápiz")
    rc = await _recepcion_confirmada(cliente, auth, prov, [p1, p2])
    movimiento_id = rc["movimientos_generados"][0]
    r = await cliente.post(
        f"/api/v1/movimientos/{movimiento_id}/anular",
        json={"nota": "Venía roto"},
        headers=auth | _clave(),
    )
    assert r.status_code == 201, r.text
    ficha = (await cliente.get(f"/api/v1/recepciones/{rc['id']}", headers=auth)).json()
    assert ficha["estado"] == "corregida"
    assert len(ficha["lineas"]) == 2, "la recepción conserva sus líneas"
    assert ficha["lineas"][0]["costo_unitario_base"] is not None, "los costos congelados siguen ahí"
    # Sigue siendo inmutable.
    editar = await cliente.patch(
        f"/api/v1/recepciones/{rc['id']}", json={"notas": "x"}, headers=auth
    )
    assert editar.status_code == 409


async def test_rf_com_013_listado_de_recepciones_con_filtros(cliente: httpx.AsyncClient) -> None:
    auth = await _sesion(cliente)
    a, b = await _proveedor(cliente, auth, "A"), await _proveedor(cliente, auth, "B")
    p = await _producto(cliente, auth)
    vieja = await _recepcion_confirmada(cliente, auth, a, [p], fecha="2026-01-15")
    nueva = await _recepcion_confirmada(cliente, auth, a, [p], fecha="2026-09-01")
    de_b = await _recepcion_confirmada(cliente, auth, b, [p], fecha="2026-09-02")
    borrador = await cliente.post(
        "/api/v1/recepciones",
        json={
            "proveedor_id": a["id"],
            "lineas": [
                {
                    "producto_id": p["id"],
                    "cantidad": "1",
                    "costo_unitario": {"monto": "1", "moneda": "COP"},
                }
            ],
        },
        headers=auth,
    )

    async def ids(**params: object) -> list[str]:
        r = await cliente.get("/api/v1/recepciones", params=params, headers=auth)
        assert r.status_code == 200, r.text
        return [x["id"] for x in r.json()["datos"]]

    assert await ids(proveedor_id=b["id"]) == [de_b["id"]]
    assert await ids(estado="borrador") == [borrador.json()["id"]]
    assert set(await ids(desde="2026-09-01", hasta="2026-09-30")) == {
        nueva["id"],
        de_b["id"],
        borrador.json()["id"],
    }
    assert await ids(proveedor_id=a["id"], estado="confirmada", hasta="2026-06-30") == [vieja["id"]]
    primera = await cliente.get("/api/v1/recepciones", params={"limit": 2}, headers=auth)
    assert primera.json()["tiene_mas"]
    segunda = await cliente.get(
        "/api/v1/recepciones",
        params={"limit": 2, "cursor": primera.json()["cursor_siguiente"]},
        headers=auth,
    )
    todos = [x["id"] for x in primera.json()["datos"]] + [x["id"] for x in segunda.json()["datos"]]
    assert len(todos) == 4 and len(set(todos)) == 4


async def test_rf_com_013_listado_de_ordenes_con_filtros(cliente: httpx.AsyncClient) -> None:
    auth = await _sesion(cliente)
    a, b = await _proveedor(cliente, auth, "A"), await _proveedor(cliente, auth, "B")
    p = await _producto(cliente, auth)

    async def orden(prov: dict) -> dict:
        r = await cliente.post(
            "/api/v1/ordenes-compra",
            json={
                "proveedor_id": prov["id"],
                "lineas": [{"producto_id": p["id"], "cantidad": "1"}],
            },
            headers=auth,
        )
        return r.json()

    o1, o2, o3 = await orden(a), await orden(a), await orden(b)
    await cliente.post(f"/api/v1/ordenes-compra/{o2['id']}/emitir", headers=auth)

    async def ids(**params: object) -> list[str]:
        r = await cliente.get("/api/v1/ordenes-compra", params=params, headers=auth)
        assert r.status_code == 200, r.text
        return [x["id"] for x in r.json()["datos"]]

    assert await ids(proveedor_id=b["id"]) == [o3["id"]]
    assert await ids(estado="emitida") == [o2["id"]]
    assert await ids(proveedor_id=a["id"], estado="borrador") == [o1["id"]]
    hoy = o1["created_at"][:10]
    assert set(await ids(desde=hoy, hasta=hoy)) == {o1["id"], o2["id"], o3["id"]}
    assert await ids(hasta="2000-01-01") == []


async def test_rf_aut_007_los_listados_no_mezclan_negocios(cliente: httpx.AsyncClient) -> None:
    a, b = await _sesion(cliente), await _sesion(cliente)
    prov = await _proveedor(cliente, a)
    p = await _producto(cliente, a)
    await _recepcion_confirmada(cliente, a, prov, [p])
    assert (await cliente.get("/api/v1/recepciones", headers=b)).json()["datos"] == []
    assert (await cliente.get("/api/v1/ordenes-compra", headers=b)).json()["datos"] == []
