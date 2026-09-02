"""RF-INV-004 y RN-03: escrituras simultáneas sobre el mismo producto no dejan stock negativo."""

import asyncio
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


async def _mover(cliente: httpx.AsyncClient, auth: dict, **cuerpo: object) -> httpx.Response:
    return await cliente.post(
        "/api/v1/movimientos", json=cuerpo, headers=auth | {"Idempotency-Key": str(uuid.uuid4())}
    )


async def test_rf_inv_004_veinte_salidas_simultaneas_sobre_stock_3_exactamente_3_tienen_exito(
    cliente: httpx.AsyncClient, sesion: AsyncSession
) -> None:
    auth = await _sesion(cliente)
    p = await _producto(cliente, auth)
    assert (
        await _mover(
            cliente, auth, producto_id=p["id"], tipo="entrada", cantidad="3", motivo="carga_inicial"
        )
    ).status_code == 201

    respuestas = await asyncio.gather(
        *[
            _mover(cliente, auth, producto_id=p["id"], tipo="salida", cantidad="1", motivo="venta")
            for _ in range(20)
        ]
    )
    codigos = sorted(r.status_code for r in respuestas)
    assert codigos.count(201) == 3, codigos
    assert codigos.count(409) == 17, codigos
    assert all(
        r.json()["error"]["code"] == "STOCK_INSUFICIENTE"
        for r in respuestas
        if r.status_code == 409
    )

    stock = await cliente.get(f"/api/v1/productos/{p['id']}/stock", headers=auth)
    assert stock.json()["cantidad"] == "0.000"
    minimo = (
        await sesion.execute(sa.text("select min(stock_resultante) from movimientos"))
    ).scalar_one()
    assert minimo >= 0, "ningún movimiento dejó el stock negativo sin forzar"
    suma = (
        await sesion.execute(sa.text("select sum(cantidad * direccion) from movimientos"))
    ).scalar_one()
    assert suma == 0


async def test_rn_03_entradas_y_salidas_simultaneas_terminan_con_la_suma_correcta(
    cliente: httpx.AsyncClient,
) -> None:
    auth = await _sesion(cliente)
    p = await _producto(cliente, auth)
    await _mover(
        cliente, auth, producto_id=p["id"], tipo="entrada", cantidad="50", motivo="carga_inicial"
    )
    tareas = [
        _mover(
            cliente, auth, producto_id=p["id"], tipo="entrada", cantidad="2", motivo="carga_inicial"
        )
        for _ in range(10)
    ] + [
        _mover(cliente, auth, producto_id=p["id"], tipo="salida", cantidad="3", motivo="venta")
        for _ in range(10)
    ]
    respuestas = await asyncio.gather(*tareas)
    assert all(r.status_code == 201 for r in respuestas), [r.text for r in respuestas]
    stock = await cliente.get(f"/api/v1/productos/{p['id']}/stock", headers=auth)
    assert stock.json()["cantidad"] == "40.000"
    historial = await cliente.get(
        f"/api/v1/productos/{p['id']}/movimientos", params={"limit": 100}, headers=auth
    )
    resultantes = [f["stock_resultante"] for f in historial.json()["datos"]]
    assert resultantes[0] == "40.000"


async def test_rf_inv_004_dos_productos_distintos_no_se_estorban(
    cliente: httpx.AsyncClient,
) -> None:
    auth = await _sesion(cliente)
    a = await _producto(cliente, auth)
    b = await _producto(cliente, auth)
    respuestas = await asyncio.gather(
        *[
            _mover(
                cliente, auth, producto_id=pid, tipo="entrada", cantidad="1", motivo="carga_inicial"
            )
            for pid in [a["id"], b["id"]] * 5
        ]
    )
    assert all(r.status_code == 201 for r in respuestas)
    for pid in (a["id"], b["id"]):
        stock = await cliente.get(f"/api/v1/productos/{pid}/stock", headers=auth)
        assert stock.json()["cantidad"] == "5.000"
