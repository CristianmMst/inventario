"""RF-INV-011, RNF-06, RN-20: POST /movimientos es idempotente por Idempotency-Key."""

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


async def _producto(cliente: httpx.AsyncClient, auth: dict) -> dict:
    r = await cliente.post(
        "/api/v1/productos", json={"nombre": "Cuaderno", "unidad_codigo": "unidad"}, headers=auth
    )
    return r.json()


async def _mover(
    cliente: httpx.AsyncClient, auth: dict, clave: str, **cuerpo: object
) -> httpx.Response:
    return await cliente.post(
        "/api/v1/movimientos", json=cuerpo, headers=auth | {"Idempotency-Key": clave}
    )


async def test_rnf_06_cinco_envios_con_la_misma_clave_producen_un_solo_movimiento(
    cliente: httpx.AsyncClient, sesion: AsyncSession
) -> None:
    auth = await _sesion(cliente)
    p = await _producto(cliente, auth)
    clave = str(uuid.uuid4())
    respuestas = [
        await _mover(
            cliente,
            auth,
            clave,
            producto_id=p["id"],
            tipo="entrada",
            cantidad="7",
            motivo="carga_inicial",
        )
        for _ in range(5)
    ]
    assert all(r.status_code == 201 for r in respuestas), [r.text for r in respuestas]
    assert len({r.json()["id"] for r in respuestas}) == 1
    total = (await sesion.execute(sa.text("select count(*) from movimientos"))).scalar_one()
    assert total == 1
    stock = await cliente.get(f"/api/v1/productos/{p['id']}/stock", headers=auth)
    assert stock.json()["cantidad"] == "7.000"


async def test_rf_inv_011_la_misma_clave_con_otro_cuerpo_responde_409(
    cliente: httpx.AsyncClient,
) -> None:
    auth = await _sesion(cliente)
    p = await _producto(cliente, auth)
    clave = str(uuid.uuid4())
    await _mover(
        cliente,
        auth,
        clave,
        producto_id=p["id"],
        tipo="entrada",
        cantidad="7",
        motivo="carga_inicial",
    )
    otro = await _mover(
        cliente,
        auth,
        clave,
        producto_id=p["id"],
        tipo="entrada",
        cantidad="8",
        motivo="carga_inicial",
    )
    assert otro.status_code == 409
    assert otro.json()["error"]["code"] == "CLAVE_IDEMPOTENCIA_REUTILIZADA"


async def test_rf_inv_011_un_rechazo_por_stock_no_consume_la_clave(
    cliente: httpx.AsyncClient,
) -> None:
    auth = await _sesion(cliente)
    p = await _producto(cliente, auth)
    clave = str(uuid.uuid4())
    rechazo = await _mover(
        cliente, auth, clave, producto_id=p["id"], tipo="salida", cantidad="5", motivo="venta"
    )
    assert rechazo.status_code == 409 and rechazo.json()["error"]["code"] == "STOCK_INSUFICIENTE"
    # La app ofrece forzar y Marta confirma: el reintento lleva la misma clave y otro cuerpo,
    # así que la clave debe estar libre.
    forzado = await _mover(
        cliente,
        auth,
        clave,
        producto_id=p["id"],
        tipo="salida",
        cantidad="5",
        motivo="venta",
        nota="confirmado por Marta",
        forzar=True,
    )
    assert forzado.status_code == 201, forzado.text


async def test_rn_20_sin_clave_el_movimiento_no_se_registra(cliente: httpx.AsyncClient) -> None:
    auth = await _sesion(cliente)
    p = await _producto(cliente, auth)
    r = await cliente.post(
        "/api/v1/movimientos",
        json={
            "producto_id": p["id"],
            "tipo": "entrada",
            "cantidad": "1",
            "motivo": "carga_inicial",
        },
        headers=auth,
    )
    assert r.status_code == 422
