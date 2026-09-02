"""RF-COM-006, RF-COM-007, RF-COM-011, RF-INV-014, RN-08, RN-10, RN-13: confirmar recepción."""

import uuid

import httpx
import sqlalchemy as sa
from sqlalchemy.ext.asyncio import AsyncSession

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


async def _proveedor(cliente: httpx.AsyncClient, auth: dict) -> dict:
    return (
        await cliente.post("/api/v1/proveedores", json={"nombre": "Papeles SAS"}, headers=auth)
    ).json()


async def _producto(cliente: httpx.AsyncClient, auth: dict, **extra: object) -> dict:
    cuerpo = {"nombre": "Cuaderno", "unidad_codigo": "unidad"} | extra
    return (await cliente.post("/api/v1/productos", json=cuerpo, headers=auth)).json()


def _linea(
    producto_id: str, cantidad: str = "10", costo: str = "2500", moneda: str = "COP"
) -> dict:
    return {
        "producto_id": producto_id,
        "cantidad": cantidad,
        "costo_unitario": {"monto": costo, "moneda": moneda},
    }


def _clave() -> dict[str, str]:
    return {"Idempotency-Key": str(uuid.uuid4())}


async def _borrador(
    cliente: httpx.AsyncClient, auth: dict, prov: dict, lineas: list[dict], **extra: object
) -> dict:
    r = await cliente.post(
        "/api/v1/recepciones",
        json={"proveedor_id": prov["id"], "lineas": lineas} | extra,
        headers=auth,
    )
    assert r.status_code == 201, r.text
    return r.json()


async def _confirmar(
    cliente: httpx.AsyncClient, auth: dict, recepcion_id: str, **cuerpo: object
) -> httpx.Response:
    return await cliente.post(
        f"/api/v1/recepciones/{recepcion_id}/confirmar", json=cuerpo, headers=auth | _clave()
    )


async def test_rf_com_006_confirmar_genera_una_entrada_por_linea_en_una_sola_operacion(
    cliente: httpx.AsyncClient,
) -> None:
    auth = await _sesion(cliente)
    prov = await _proveedor(cliente, auth)
    p1, p2 = await _producto(cliente, auth), await _producto(cliente, auth, nombre="Lápiz")
    rc = await _borrador(
        cliente, auth, prov, [_linea(p1["id"], "10"), _linea(p2["id"], "4", "800")]
    )
    r = await _confirmar(cliente, auth, rc["id"])
    assert r.status_code == 200, r.text
    confirmada = r.json()
    assert confirmada["estado"] == "confirmada" and confirmada["confirmada_en"]
    assert len(confirmada["movimientos_generados"]) == 2
    for pid, esperado in [(p1["id"], "10.000"), (p2["id"], "4.000")]:
        stock = await cliente.get(f"/api/v1/productos/{pid}/stock", headers=auth)
        assert stock.json()["cantidad"] == esperado


async def test_rf_inv_014_los_movimientos_quedan_enlazados_a_la_recepcion_y_su_linea(
    cliente: httpx.AsyncClient,
) -> None:
    auth = await _sesion(cliente)
    prov = await _proveedor(cliente, auth)
    p = await _producto(cliente, auth)
    rc = await _borrador(cliente, auth, prov, [_linea(p["id"])])
    confirmada = (await _confirmar(cliente, auth, rc["id"])).json()
    historial = (
        await cliente.get(f"/api/v1/productos/{p['id']}/movimientos", headers=auth)
    ).json()["datos"]
    m = historial[0]
    assert m["id"] == confirmada["movimientos_generados"][0]
    assert (
        m["tipo"] == "entrada" and m["motivo"] == "recepcion_compra" and m["origen"] == "recepcion"
    )
    assert m["recepcion_id"] == rc["id"]
    assert m["recepcion_linea_id"] == confirmada["lineas"][0]["id"]


async def test_rn_08_la_linea_congela_costo_moneda_tasa_y_equivalente_base(
    cliente: httpx.AsyncClient, sesion: AsyncSession
) -> None:
    auth = await _sesion(cliente)
    prov = await _proveedor(cliente, auth)
    p = await _producto(cliente, auth)
    rc = await _borrador(
        cliente, auth, prov, [_linea(p["id"], "10", "1.5", "USD")], moneda="USD", tasa_cambio="4000"
    )
    confirmada = (await _confirmar(cliente, auth, rc["id"])).json()
    linea = confirmada["lineas"][0]
    assert linea["costo_unitario"] == {"monto": "1.5000", "moneda": "USD"}
    assert linea["tasa_cambio"] == "4000.00000000"
    assert linea["costo_unitario_base"] == {"monto": "6000.0000", "moneda": "COP"}
    assert confirmada["total_base"] == {"monto": "60000.0000", "moneda": "COP"}

    # Cambiar el costo del producto después no altera la línea congelada.
    await cliente.patch(
        f"/api/v1/productos/{p['id']}",
        json={"costo_actual": {"monto": "9999", "moneda": "COP"}},
        headers=auth,
    )
    fila = (
        await sesion.execute(
            sa.text(
                "select costo_unitario, costo_unitario_base, tasa_cambio"
                " from recepciones_lineas where id = :id"
            ),
            {"id": linea["id"]},
        )
    ).one()
    assert str(fila.costo_unitario) == "1.5000" and str(fila.costo_unitario_base) == "6000.0000"


async def test_rf_com_011_el_costo_actual_del_producto_pasa_a_ser_el_ultimo_recibido(
    cliente: httpx.AsyncClient,
) -> None:
    auth = await _sesion(cliente)
    prov = await _proveedor(cliente, auth)
    p = await _producto(cliente, auth, costo_actual={"monto": "1000", "moneda": "COP"})
    primera = await _borrador(cliente, auth, prov, [_linea(p["id"], "10", "1200")])
    await _confirmar(cliente, auth, primera["id"])
    segunda = await _borrador(cliente, auth, prov, [_linea(p["id"], "1", "1300")])
    await _confirmar(cliente, auth, segunda["id"])
    ficha = (await cliente.get(f"/api/v1/productos/{p['id']}", headers=auth)).json()
    # Último costo recibido, no promedio ponderado (1200*10 + 1300*1)/11.
    assert ficha["costo_actual"] == {"monto": "1300.0000", "moneda": "COP"}


async def test_rn_13_si_una_linea_falla_no_entra_ninguna(
    cliente: httpx.AsyncClient, sesion: AsyncSession
) -> None:
    auth = await _sesion(cliente)
    prov = await _proveedor(cliente, auth)
    p1, p2 = await _producto(cliente, auth), await _producto(cliente, auth, nombre="Lápiz")
    rc = await _borrador(cliente, auth, prov, [_linea(p1["id"]), _linea(p2["id"])])
    # Entre el borrador y la confirmación alguien archivó el segundo producto.
    await cliente.post(f"/api/v1/productos/{p2['id']}/archivar", headers=auth)
    r = await _confirmar(cliente, auth, rc["id"])
    assert r.status_code == 409, r.text
    assert r.json()["error"]["code"] == "PRODUCTO_ARCHIVADO"
    total = (await sesion.execute(sa.text("select count(*) from movimientos"))).scalar_one()
    assert total == 0
    stock = await cliente.get(f"/api/v1/productos/{p1['id']}/stock", headers=auth)
    assert stock.json()["cantidad"] == "0.000"
    ficha = await cliente.get(f"/api/v1/recepciones/{rc['id']}", headers=auth)
    assert ficha.json()["estado"] == "borrador"


async def test_rf_com_012_confirmar_dos_veces_responde_409(cliente: httpx.AsyncClient) -> None:
    auth = await _sesion(cliente)
    prov = await _proveedor(cliente, auth)
    p = await _producto(cliente, auth)
    rc = await _borrador(cliente, auth, prov, [_linea(p["id"])])
    assert (await _confirmar(cliente, auth, rc["id"])).status_code == 200
    otra = await _confirmar(cliente, auth, rc["id"])
    assert otra.status_code == 409
    assert otra.json()["error"]["code"] == "RECEPCION_INMUTABLE"


async def test_rn_20_confirmar_es_idempotente_por_clave(
    cliente: httpx.AsyncClient, sesion: AsyncSession
) -> None:
    auth = await _sesion(cliente)
    prov = await _proveedor(cliente, auth)
    p = await _producto(cliente, auth)
    rc = await _borrador(cliente, auth, prov, [_linea(p["id"])])
    clave = _clave()
    a = await cliente.post(
        f"/api/v1/recepciones/{rc['id']}/confirmar", json={}, headers=auth | clave
    )
    b = await cliente.post(
        f"/api/v1/recepciones/{rc['id']}/confirmar", json={}, headers=auth | clave
    )
    assert a.status_code == b.status_code == 200
    assert a.json() == b.json()
    total = (await sesion.execute(sa.text("select count(*) from movimientos"))).scalar_one()
    assert total == 1


async def test_rn_10_la_moneda_base_no_cambia_una_vez_existe_un_documento_valorizado(
    cliente: httpx.AsyncClient,
) -> None:
    auth = await _sesion(cliente)
    antes = await cliente.patch("/api/v1/negocio", json={"moneda_base": "USD"}, headers=auth)
    assert antes.status_code == 200, antes.text
    assert antes.json()["moneda_base"] == "USD"
    await cliente.patch("/api/v1/negocio", json={"moneda_base": "COP"}, headers=auth)

    prov = await _proveedor(cliente, auth)
    p = await _producto(cliente, auth)
    rc = await _borrador(cliente, auth, prov, [_linea(p["id"])])
    await _confirmar(cliente, auth, rc["id"])
    despues = await cliente.patch("/api/v1/negocio", json={"moneda_base": "USD"}, headers=auth)
    assert despues.status_code == 409
    assert despues.json()["error"]["code"] == "MONEDA_BASE_INMUTABLE"
    nombre = await cliente.patch(
        "/api/v1/negocio", json={"nombre": "Papelería Marta"}, headers=auth
    )
    assert nombre.status_code == 200 and nombre.json()["nombre"] == "Papelería Marta"


async def test_rf_com_012_una_confirmada_no_se_edita(cliente: httpx.AsyncClient) -> None:
    auth = await _sesion(cliente)
    prov = await _proveedor(cliente, auth)
    p = await _producto(cliente, auth)
    rc = await _borrador(cliente, auth, prov, [_linea(p["id"])])
    await _confirmar(cliente, auth, rc["id"])
    r = await cliente.patch(f"/api/v1/recepciones/{rc['id']}", json={"notas": "x"}, headers=auth)
    assert r.status_code == 409
    assert r.json()["error"]["code"] == "RECEPCION_INMUTABLE"
