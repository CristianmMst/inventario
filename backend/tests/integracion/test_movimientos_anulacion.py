"""RF-INV-008 y RN-02: anular crea un contramovimiento que referencia al original."""

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


def _clave() -> dict[str, str]:
    return {"Idempotency-Key": str(uuid.uuid4())}


async def _mover(cliente: httpx.AsyncClient, auth: dict, **cuerpo: object) -> dict:
    r = await cliente.post("/api/v1/movimientos", json=cuerpo, headers=auth | _clave())
    assert r.status_code == 201, r.text
    return r.json()


async def _anular(
    cliente: httpx.AsyncClient, auth: dict, movimiento_id: str, nota: str | None = "Error de dedo"
) -> httpx.Response:
    cuerpo = {"nota": nota} if nota is not None else {}
    return await cliente.post(
        f"/api/v1/movimientos/{movimiento_id}/anular", json=cuerpo, headers=auth | _clave()
    )


async def test_rf_inv_008_anular_crea_el_inverso_y_devuelve_el_stock(
    cliente: httpx.AsyncClient,
) -> None:
    auth = await _sesion(cliente)
    p = await _producto(cliente, auth)
    await _mover(
        cliente, auth, producto_id=p["id"], tipo="entrada", cantidad="10", motivo="carga_inicial"
    )
    salida = await _mover(
        cliente, auth, producto_id=p["id"], tipo="salida", cantidad="3", motivo="venta"
    )

    anulacion = await _anular(cliente, auth, salida["id"])
    assert anulacion.status_code == 201, anulacion.text
    contra = anulacion.json()
    assert contra["tipo"] == "contramovimiento"
    assert contra["cantidad"] == "3.000"
    assert contra["direccion"] == 1
    assert contra["anula_movimiento_id"] == salida["id"]
    assert contra["motivo"] == "anulacion"
    assert contra["nota"] == "Error de dedo"
    assert contra["stock_resultante"] == "10.000"

    original = await cliente.get(f"/api/v1/movimientos/{salida['id']}", headers=auth)
    assert original.status_code == 200
    assert original.json()["anulado_en"] is not None
    stock = await cliente.get(f"/api/v1/productos/{p['id']}/stock", headers=auth)
    assert stock.json()["cantidad"] == "10.000"


async def test_rf_inv_008_anular_dos_veces_responde_409(cliente: httpx.AsyncClient) -> None:
    auth = await _sesion(cliente)
    p = await _producto(cliente, auth)
    entrada = await _mover(
        cliente, auth, producto_id=p["id"], tipo="entrada", cantidad="1", motivo="carga_inicial"
    )
    assert (await _anular(cliente, auth, entrada["id"])).status_code == 201
    segunda = await _anular(cliente, auth, entrada["id"])
    assert segunda.status_code == 409
    assert segunda.json()["error"]["code"] == "MOVIMIENTO_YA_ANULADO"


async def test_rf_inv_008_anular_un_contramovimiento_responde_409(
    cliente: httpx.AsyncClient,
) -> None:
    auth = await _sesion(cliente)
    p = await _producto(cliente, auth)
    entrada = await _mover(
        cliente, auth, producto_id=p["id"], tipo="entrada", cantidad="1", motivo="carga_inicial"
    )
    contra = (await _anular(cliente, auth, entrada["id"])).json()
    r = await _anular(cliente, auth, contra["id"])
    assert r.status_code == 409
    assert r.json()["error"]["code"] == "CONTRAMOVIMIENTO_NO_ANULABLE"


async def test_rf_inv_010_la_anulacion_exige_nota(cliente: httpx.AsyncClient) -> None:
    auth = await _sesion(cliente)
    p = await _producto(cliente, auth)
    entrada = await _mover(
        cliente, auth, producto_id=p["id"], tipo="entrada", cantidad="1", motivo="carga_inicial"
    )
    sin_nota = await _anular(cliente, auth, entrada["id"], nota=None)
    assert sin_nota.status_code == 422
    assert sin_nota.json()["error"]["code"] == "NOTA_OBLIGATORIA"


async def test_rn_02_anular_una_entrada_ya_vendida_puede_dejar_el_stock_negativo(
    cliente: httpx.AsyncClient,
) -> None:
    """La anulación corrige un hecho equivocado: si la entrada nunca existió, el stock real es
    el que queda, aunque sea negativo. RN-03 habla de salidas y mermas, no de correcciones."""
    auth = await _sesion(cliente)
    p = await _producto(cliente, auth)
    entrada = await _mover(
        cliente, auth, producto_id=p["id"], tipo="entrada", cantidad="5", motivo="carga_inicial"
    )
    await _mover(cliente, auth, producto_id=p["id"], tipo="salida", cantidad="4", motivo="venta")
    contra = await _anular(cliente, auth, entrada["id"])
    assert contra.status_code == 201, contra.text
    assert contra.json()["stock_resultante"] == "-4.000"


async def test_rf_inv_011_anular_es_idempotente_por_clave(cliente: httpx.AsyncClient) -> None:
    auth = await _sesion(cliente)
    p = await _producto(cliente, auth)
    entrada = await _mover(
        cliente, auth, producto_id=p["id"], tipo="entrada", cantidad="1", motivo="carga_inicial"
    )
    clave = _clave()
    a = await cliente.post(
        f"/api/v1/movimientos/{entrada['id']}/anular", json={"nota": "x"}, headers=auth | clave
    )
    b = await cliente.post(
        f"/api/v1/movimientos/{entrada['id']}/anular", json={"nota": "x"}, headers=auth | clave
    )
    assert a.status_code == b.status_code == 201
    assert a.json()["id"] == b.json()["id"]


async def test_rf_aut_007_anular_un_movimiento_ajeno_responde_404(
    cliente: httpx.AsyncClient,
) -> None:
    a, b = await _sesion(cliente), await _sesion(cliente)
    p = await _producto(cliente, a)
    entrada = await _mover(
        cliente, a, producto_id=p["id"], tipo="entrada", cantidad="1", motivo="carga_inicial"
    )
    r = await _anular(cliente, b, entrada["id"])
    assert r.status_code == 404
    assert r.json()["error"]["code"] == "MOVIMIENTO_NO_ENCONTRADO"
    assert (await cliente.get(f"/api/v1/movimientos/{entrada['id']}", headers=b)).status_code == 404
