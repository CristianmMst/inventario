"""RF-COM-008, RF-COM-009, RF-COM-003, RN-12: recepción parcial, exceso y cierre con faltante."""

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


async def _preparar(cliente: httpx.AsyncClient, auth: dict, ordenada: str = "100") -> dict:
    prov = (await cliente.post("/api/v1/proveedores", json={"nombre": "P"}, headers=auth)).json()
    prod = (
        await cliente.post(
            "/api/v1/productos",
            json={"nombre": "Cuaderno", "unidad_codigo": "unidad"},
            headers=auth,
        )
    ).json()
    o = (
        await cliente.post(
            "/api/v1/ordenes-compra",
            json={
                "proveedor_id": prov["id"],
                "lineas": [{"producto_id": prod["id"], "cantidad": ordenada}],
            },
            headers=auth,
        )
    ).json()
    emitida = await cliente.post(f"/api/v1/ordenes-compra/{o['id']}/emitir", headers=auth)
    assert emitida.status_code == 200
    return {"proveedor": prov, "producto": prod, "orden": emitida.json()}


async def _recibir(
    cliente: httpx.AsyncClient, auth: dict, ctx: dict, cantidad: str, **confirmacion: object
) -> httpx.Response:
    rc = await cliente.post(
        "/api/v1/recepciones",
        json={
            "proveedor_id": ctx["proveedor"]["id"],
            "orden_id": ctx["orden"]["id"],
            "lineas": [
                {
                    "producto_id": ctx["producto"]["id"],
                    "cantidad": cantidad,
                    "costo_unitario": {"monto": "10", "moneda": "COP"},
                }
            ],
        },
        headers=auth,
    )
    assert rc.status_code == 201, rc.text
    return await cliente.post(
        f"/api/v1/recepciones/{rc.json()['id']}/confirmar",
        json=confirmacion,
        headers=auth | _clave(),
    )


async def _orden(cliente: httpx.AsyncClient, auth: dict, orden_id: str) -> dict:
    return (await cliente.get(f"/api/v1/ordenes-compra/{orden_id}", headers=auth)).json()


async def test_rf_com_008_recibir_60_de_100_deja_parcialmente_recibida_con_40_pendientes(
    cliente: httpx.AsyncClient,
) -> None:
    auth = await _sesion(cliente)
    ctx = await _preparar(cliente, auth)
    r = await _recibir(cliente, auth, ctx, "60")
    assert r.status_code == 200, r.text
    orden = await _orden(cliente, auth, ctx["orden"]["id"])
    assert orden["estado"] == "parcialmente_recibida"
    assert orden["lineas"][0]["cantidad_recibida"] == "60.000"
    assert orden["lineas"][0]["cantidad_pendiente"] == "40.000"


async def test_rf_com_005_varias_recepciones_completan_la_orden(cliente: httpx.AsyncClient) -> None:
    auth = await _sesion(cliente)
    ctx = await _preparar(cliente, auth)
    await _recibir(cliente, auth, ctx, "60")
    r = await _recibir(cliente, auth, ctx, "40")
    assert r.status_code == 200, r.text
    orden = await _orden(cliente, auth, ctx["orden"]["id"])
    assert orden["estado"] == "recibida"
    assert orden["lineas"][0]["cantidad_pendiente"] == "0.000"


async def test_rf_com_009_recibir_120_sin_confirmar_el_exceso_responde_409(
    cliente: httpx.AsyncClient,
) -> None:
    auth = await _sesion(cliente)
    ctx = await _preparar(cliente, auth)
    r = await _recibir(cliente, auth, ctx, "120")
    assert r.status_code == 409, r.text
    error = r.json()["error"]
    assert error["code"] == "EXCESO_SOBRE_ORDEN"
    assert error["details"]["lineas"][0]["pendiente"] == "100.000"
    assert error["details"]["lineas"][0]["recibido"] == "120.000"
    stock = await cliente.get(f"/api/v1/productos/{ctx['producto']['id']}/stock", headers=auth)
    assert stock.json()["cantidad"] == "0.000"


async def test_rf_com_009_con_confirmacion_explicita_el_exceso_se_registra_en_la_linea(
    cliente: httpx.AsyncClient,
) -> None:
    auth = await _sesion(cliente)
    ctx = await _preparar(cliente, auth)
    r = await _recibir(cliente, auth, ctx, "120", confirmar_exceso=True)
    assert r.status_code == 200, r.text
    assert r.json()["lineas"][0]["exceso"] is True
    orden = await _orden(cliente, auth, ctx["orden"]["id"])
    assert orden["estado"] == "recibida"
    assert orden["lineas"][0]["cantidad_recibida"] == "120.000"
    stock = await cliente.get(f"/api/v1/productos/{ctx['producto']['id']}/stock", headers=auth)
    assert stock.json()["cantidad"] == "120.000"


async def test_rf_com_008_cerrar_con_faltante_indica_motivo_y_no_admite_mas_recepciones(
    cliente: httpx.AsyncClient,
) -> None:
    auth = await _sesion(cliente)
    ctx = await _preparar(cliente, auth)
    await _recibir(cliente, auth, ctx, "60")
    r = await cliente.post(
        f"/api/v1/ordenes-compra/{ctx['orden']['id']}/cerrar-con-faltante",
        json={"motivo": "El proveedor descontinuó el producto"},
        headers=auth,
    )
    assert r.status_code == 200, r.text
    assert r.json()["estado"] == "cerrada_con_faltante"
    assert r.json()["motivo_cierre"] == "El proveedor descontinuó el producto"
    assert r.json()["lineas"][0]["cantidad_pendiente"] == "40.000"
    # Ni siquiera se puede abrir un borrador contra ella.
    otra = await cliente.post(
        "/api/v1/recepciones",
        json={
            "proveedor_id": ctx["proveedor"]["id"],
            "orden_id": ctx["orden"]["id"],
            "lineas": [
                {
                    "producto_id": ctx["producto"]["id"],
                    "cantidad": "10",
                    "costo_unitario": {"monto": "10", "moneda": "COP"},
                }
            ],
        },
        headers=auth,
    )
    assert otra.status_code == 409
    assert otra.json()["error"]["code"] == "ORDEN_NO_RECIBIBLE"


async def test_rf_com_008_cerrar_con_faltante_exige_motivo_y_estado_parcial(
    cliente: httpx.AsyncClient,
) -> None:
    auth = await _sesion(cliente)
    ctx = await _preparar(cliente, auth)
    sin_recepciones = await cliente.post(
        f"/api/v1/ordenes-compra/{ctx['orden']['id']}/cerrar-con-faltante",
        json={"motivo": "x"},
        headers=auth,
    )
    assert sin_recepciones.status_code == 409
    assert sin_recepciones.json()["error"]["code"] == "TRANSICION_INVALIDA"
    await _recibir(cliente, auth, ctx, "60")
    sin_motivo = await cliente.post(
        f"/api/v1/ordenes-compra/{ctx['orden']['id']}/cerrar-con-faltante", json={}, headers=auth
    )
    assert sin_motivo.status_code == 422


async def test_rf_com_010_una_orden_con_recepciones_no_se_cancela(
    cliente: httpx.AsyncClient,
) -> None:
    auth = await _sesion(cliente)
    ctx = await _preparar(cliente, auth)
    await _recibir(cliente, auth, ctx, "60")
    r = await cliente.post(
        f"/api/v1/ordenes-compra/{ctx['orden']['id']}/cancelar", json={"motivo": "x"}, headers=auth
    )
    assert r.status_code == 409
    assert r.json()["error"]["code"] == "ORDEN_CON_RECEPCIONES"
    assert r.json()["error"]["details"]["accion_sugerida"] == "cerrar-con-faltante"


async def test_rn_12_una_recepcion_en_borrador_no_cuenta_como_recibido(
    cliente: httpx.AsyncClient,
) -> None:
    auth = await _sesion(cliente)
    ctx = await _preparar(cliente, auth)
    rc = await cliente.post(
        "/api/v1/recepciones",
        json={
            "proveedor_id": ctx["proveedor"]["id"],
            "orden_id": ctx["orden"]["id"],
            "lineas": [
                {
                    "producto_id": ctx["producto"]["id"],
                    "cantidad": "60",
                    "costo_unitario": {"monto": "10", "moneda": "COP"},
                }
            ],
        },
        headers=auth,
    )
    assert rc.status_code == 201
    orden = await _orden(cliente, auth, ctx["orden"]["id"])
    assert orden["estado"] == "emitida"
    assert orden["lineas"][0]["cantidad_recibida"] == "0.000"
