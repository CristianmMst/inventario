"""RF-COM-002: órdenes de compra en borrador; la cantidad pendiente se calcula, no se guarda."""

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


def _linea(producto_id: str, cantidad: str = "100", costo: str | None = "2500") -> dict:
    linea: dict[str, object] = {"producto_id": producto_id, "cantidad": cantidad}
    if costo is not None:
        linea["costo_unitario_estimado"] = {"monto": costo, "moneda": "COP"}
    return linea


async def _orden(
    cliente: httpx.AsyncClient, auth: dict, proveedor_id: str, lineas: list[dict], **extra: object
) -> httpx.Response:
    cuerpo = {"proveedor_id": proveedor_id, "lineas": lineas} | extra
    return await cliente.post("/api/v1/ordenes-compra", json=cuerpo, headers=auth)


async def test_rf_com_002_alta_en_borrador_con_lineas_y_numero_correlativo(
    cliente: httpx.AsyncClient,
) -> None:
    auth = await _sesion(cliente)
    prov = await _proveedor(cliente, auth)
    p1, p2 = await _producto(cliente, auth), await _producto(cliente, auth, nombre="Lápiz")
    r = await _orden(
        cliente,
        auth,
        prov["id"],
        [_linea(p1["id"], "100", "2500"), _linea(p2["id"], "50", "800")],
        fecha_esperada="2026-09-15",
        notas="Pedido de septiembre",
    )
    assert r.status_code == 201, r.text
    o = r.json()
    assert o["estado"] == "borrador"
    assert o["numero"] == "OC-000001"
    assert o["proveedor"] == {"id": prov["id"], "nombre": "Papeles SAS"}
    assert o["moneda"] == "COP" and o["fecha_esperada"] == "2026-09-15"
    assert len(o["lineas"]) == 2
    linea = o["lineas"][0]
    assert linea["producto"]["id"] == p1["id"]
    assert linea["cantidad_ordenada"] == "100.000"
    assert linea["costo_unitario_estimado"] == {"monto": "2500.0000", "moneda": "COP"}
    assert linea["cantidad_recibida"] == "0.000" and linea["cantidad_pendiente"] == "100.000"
    assert o["total_estimado"] == {"monto": "290000.0000", "moneda": "COP"}

    segunda = await _orden(cliente, auth, prov["id"], [_linea(p1["id"])])
    assert segunda.json()["numero"] == "OC-000002"


async def test_rf_com_002_la_cantidad_pendiente_no_se_guarda_en_la_base(
    sesion: AsyncSession,
) -> None:
    columnas = (
        await sesion.execute(
            sa.text(
                "select column_name from information_schema.columns"
                " where table_name = 'ordenes_compra_lineas'"
            )
        )
    ).scalars()
    assert "cantidad_pendiente" not in set(columnas)
    assert "cantidad_recibida" not in set(columnas)


async def test_rf_com_002_las_lineas_se_editan_en_borrador(cliente: httpx.AsyncClient) -> None:
    auth = await _sesion(cliente)
    prov = await _proveedor(cliente, auth)
    p1, p2 = await _producto(cliente, auth), await _producto(cliente, auth, nombre="Lápiz")
    o = (await _orden(cliente, auth, prov["id"], [_linea(p1["id"], "100")])).json()
    r = await cliente.patch(
        f"/api/v1/ordenes-compra/{o['id']}",
        json={"lineas": [_linea(p2["id"], "7", "10")], "notas": "cambiado"},
        headers=auth,
    )
    assert r.status_code == 200, r.text
    assert [linea["producto"]["id"] for linea in r.json()["lineas"]] == [p2["id"]]
    assert r.json()["notas"] == "cambiado"
    assert r.json()["total_estimado"]["monto"] == "70.0000"


async def test_rf_com_002_una_orden_exige_al_menos_una_linea_y_cantidades_validas(
    cliente: httpx.AsyncClient,
) -> None:
    auth = await _sesion(cliente)
    prov = await _proveedor(cliente, auth)
    p = await _producto(cliente, auth)
    sin_lineas = await _orden(cliente, auth, prov["id"], [])
    assert sin_lineas.status_code == 422
    decimal_en_unidad = await _orden(cliente, auth, prov["id"], [_linea(p["id"], "2.5")])
    assert decimal_en_unidad.status_code == 422
    assert decimal_en_unidad.json()["error"]["code"] == "CANTIDAD_INVALIDA_PARA_UNIDAD"
    cero = await _orden(cliente, auth, prov["id"], [_linea(p["id"], "0")])
    assert cero.status_code == 422
    repetido = await _orden(cliente, auth, prov["id"], [_linea(p["id"]), _linea(p["id"])])
    assert repetido.status_code == 422
    assert repetido.json()["error"]["code"] == "PRODUCTO_REPETIDO"


async def test_rf_com_002_la_moneda_de_los_costos_debe_ser_la_de_la_orden(
    cliente: httpx.AsyncClient,
) -> None:
    auth = await _sesion(cliente)
    prov = await _proveedor(cliente, auth)
    p = await _producto(cliente, auth)
    r = await _orden(
        cliente,
        auth,
        prov["id"],
        [
            {
                "producto_id": p["id"],
                "cantidad": "1",
                "costo_unitario_estimado": {"monto": "1", "moneda": "COP"},
            }
        ],
        moneda="USD",
    )
    assert r.status_code == 422
    assert r.json()["error"]["code"] == "MONEDAS_DISTINTAS"
    ok = await _orden(
        cliente,
        auth,
        prov["id"],
        [
            {
                "producto_id": p["id"],
                "cantidad": "1",
                "costo_unitario_estimado": {"monto": "1.5", "moneda": "USD"},
            }
        ],
        moneda="USD",
    )
    assert ok.status_code == 201
    assert ok.json()["total_estimado"] == {"monto": "1.5000", "moneda": "USD"}


async def test_rn_17_un_proveedor_archivado_no_es_seleccionable(cliente: httpx.AsyncClient) -> None:
    auth = await _sesion(cliente)
    prov = await _proveedor(cliente, auth)
    p = await _producto(cliente, auth)
    await cliente.post(f"/api/v1/proveedores/{prov['id']}/archivar", headers=auth)
    r = await _orden(cliente, auth, prov["id"], [_linea(p["id"])])
    assert r.status_code == 422
    assert r.json()["error"]["code"] == "PROVEEDOR_ARCHIVADO"


async def test_rn_17_un_proveedor_con_ordenes_no_se_borra_y_sigue_nombrado(
    cliente: httpx.AsyncClient,
) -> None:
    auth = await _sesion(cliente)
    prov = await _proveedor(cliente, auth)
    p = await _producto(cliente, auth)
    o = (await _orden(cliente, auth, prov["id"], [_linea(p["id"])])).json()
    borrado = await cliente.delete(f"/api/v1/proveedores/{prov['id']}", headers=auth)
    assert borrado.status_code == 409
    assert borrado.json()["error"]["code"] == "PROVEEDOR_CON_DOCUMENTOS"
    assert borrado.json()["error"]["details"]["accion_sugerida"] == "archivar"
    await cliente.post(f"/api/v1/proveedores/{prov['id']}/archivar", headers=auth)
    ficha = await cliente.get(f"/api/v1/ordenes-compra/{o['id']}", headers=auth)
    assert ficha.json()["proveedor"]["nombre"] == "Papeles SAS"


async def test_rf_cat_011_un_producto_archivado_no_entra_en_una_orden_nueva(
    cliente: httpx.AsyncClient,
) -> None:
    auth = await _sesion(cliente)
    prov = await _proveedor(cliente, auth)
    p = await _producto(cliente, auth)
    await cliente.post(f"/api/v1/productos/{p['id']}/archivar", headers=auth)
    r = await _orden(cliente, auth, prov["id"], [_linea(p["id"])])
    assert r.status_code == 409
    assert r.json()["error"]["code"] == "PRODUCTO_ARCHIVADO"


async def test_rf_aut_007_la_orden_de_otro_negocio_no_existe(cliente: httpx.AsyncClient) -> None:
    a, b = await _sesion(cliente), await _sesion(cliente)
    prov = await _proveedor(cliente, a)
    p = await _producto(cliente, a)
    o = (await _orden(cliente, a, prov["id"], [_linea(p["id"])])).json()
    assert (await cliente.get(f"/api/v1/ordenes-compra/{o['id']}", headers=b)).status_code == 404
    ajeno = await _orden(cliente, b, prov["id"], [_linea(p["id"])])
    assert ajeno.status_code == 404
    assert ajeno.json()["error"]["code"] == "PROVEEDOR_NO_ENCONTRADO"
    assert (await cliente.get(f"/api/v1/ordenes-compra/{uuid.uuid4()}", headers=a)).json()["error"][
        "code"
    ] == "ORDEN_NO_ENCONTRADA"
