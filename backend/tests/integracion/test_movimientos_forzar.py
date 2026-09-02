"""RF-INV-006 y RN-04: el bloqueo de stock negativo se puede forzar con confirmación explícita."""

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


async def _producto(cliente: httpx.AsyncClient, auth: dict) -> dict:
    r = await cliente.post(
        "/api/v1/productos", json={"nombre": "Cuaderno", "unidad_codigo": "unidad"}, headers=auth
    )
    return r.json()


async def _mover(cliente: httpx.AsyncClient, auth: dict, **cuerpo: object) -> httpx.Response:
    return await cliente.post(
        "/api/v1/movimientos", json=cuerpo, headers=auth | {"Idempotency-Key": str(uuid.uuid4())}
    )


async def test_rf_inv_006_con_forzar_el_movimiento_se_registra_marcado_y_el_stock_queda_negativo(
    cliente: httpx.AsyncClient,
) -> None:
    auth = await _sesion(cliente)
    p = await _producto(cliente, auth)
    await _mover(
        cliente, auth, producto_id=p["id"], tipo="entrada", cantidad="2", motivo="carga_inicial"
    )
    forzada = await _mover(
        cliente,
        auth,
        producto_id=p["id"],
        tipo="salida",
        cantidad="5",
        motivo="venta",
        nota="Se vendió lo que no estaba cargado",
        forzar=True,
    )
    assert forzada.status_code == 201, forzada.text
    assert forzada.json()["forzado"] is True
    assert forzada.json()["stock_resultante"] == "-3.000"
    stock = await cliente.get(f"/api/v1/productos/{p['id']}/stock", headers=auth)
    assert stock.json()["cantidad"] == "-3.000"


async def test_rn_04_forzar_exige_nota(cliente: httpx.AsyncClient) -> None:
    auth = await _sesion(cliente)
    p = await _producto(cliente, auth)
    r = await _mover(
        cliente, auth, producto_id=p["id"], tipo="salida", cantidad="5", motivo="venta", forzar=True
    )
    assert r.status_code == 422
    assert r.json()["error"]["code"] == "NOTA_OBLIGATORIA"


async def test_rn_04_forzar_sin_necesidad_no_marca_el_movimiento_como_forzado(
    cliente: httpx.AsyncClient,
) -> None:
    """Solo queda marcado el movimiento que de verdad saltó el bloqueo: así el reporte de
    discrepancias muestra discrepancias y no ventas normales con la casilla marcada."""
    auth = await _sesion(cliente)
    p = await _producto(cliente, auth)
    await _mover(
        cliente, auth, producto_id=p["id"], tipo="entrada", cantidad="10", motivo="carga_inicial"
    )
    r = await _mover(
        cliente,
        auth,
        producto_id=p["id"],
        tipo="salida",
        cantidad="2",
        motivo="venta",
        nota="por si acaso",
        forzar=True,
    )
    assert r.status_code == 201
    assert r.json()["forzado"] is False


async def test_rn_04_la_merma_tambien_se_puede_forzar(cliente: httpx.AsyncClient) -> None:
    auth = await _sesion(cliente)
    p = await _producto(cliente, auth)
    r = await _mover(
        cliente,
        auth,
        producto_id=p["id"],
        tipo="merma",
        cantidad="1",
        motivo="rotura",
        nota="Rota antes de cargarla",
        forzar=True,
    )
    assert r.status_code == 201
    assert r.json()["forzado"] is True and r.json()["stock_resultante"] == "-1.000"


async def test_rn_04_un_stock_negativo_se_recupera_con_una_entrada(
    cliente: httpx.AsyncClient,
) -> None:
    auth = await _sesion(cliente)
    p = await _producto(cliente, auth)
    await _mover(
        cliente,
        auth,
        producto_id=p["id"],
        tipo="salida",
        cantidad="3",
        motivo="venta",
        nota="forzada",
        forzar=True,
    )
    entrada = await _mover(
        cliente, auth, producto_id=p["id"], tipo="entrada", cantidad="10", motivo="carga_inicial"
    )
    assert entrada.json()["stock_resultante"] == "7.000"
