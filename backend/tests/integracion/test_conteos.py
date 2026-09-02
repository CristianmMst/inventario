"""RF-INV-013 y RN-15: ajuste por conteo físico; el servidor calcula el delta."""

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


async def _producto(cliente: httpx.AsyncClient, auth: dict, **extra: object) -> dict:
    cuerpo = {"nombre": "Cuaderno", "unidad_codigo": "unidad"} | extra
    r = await cliente.post("/api/v1/productos", json=cuerpo, headers=auth)
    return r.json()


def _clave() -> dict[str, str]:
    return {"Idempotency-Key": str(uuid.uuid4())}


async def _cargar(cliente: httpx.AsyncClient, auth: dict, producto_id: str, cantidad: str) -> None:
    r = await cliente.post(
        "/api/v1/movimientos",
        json={
            "producto_id": producto_id,
            "tipo": "entrada",
            "cantidad": cantidad,
            "motivo": "carga_inicial",
        },
        headers=auth | _clave(),
    )
    assert r.status_code == 201, r.text


async def _contar(
    cliente: httpx.AsyncClient,
    auth: dict,
    producto_id: str,
    contada: object,
    nota: str | None = "Conteo de cierre",
) -> httpx.Response:
    cuerpo: dict[str, object] = {"cantidad_contada": contada}
    if nota is not None:
        cuerpo["nota"] = nota
    return await cliente.post(
        f"/api/v1/productos/{producto_id}/conteo", json=cuerpo, headers=auth | _clave()
    )


async def test_rf_inv_013_contar_8_sobre_10_crea_un_ajuste_de_menos_2(
    cliente: httpx.AsyncClient,
) -> None:
    auth = await _sesion(cliente)
    p = await _producto(cliente, auth)
    await _cargar(cliente, auth, p["id"], "10")
    r = await _contar(cliente, auth, p["id"], "8")
    assert r.status_code == 200, r.text
    datos = r.json()
    assert datos["stock_anterior"] == "10.000"
    assert datos["cantidad_contada"] == "8.000"
    assert datos["diferencia"] == "-2.000"
    movimiento = datos["movimiento"]
    assert movimiento["tipo"] == "ajuste"
    assert movimiento["cantidad"] == "2.000" and movimiento["direccion"] == -1
    assert movimiento["motivo"] == "conteo_fisico"
    assert movimiento["stock_resultante"] == "8.000"
    stock = await cliente.get(f"/api/v1/productos/{p['id']}/stock", headers=auth)
    assert stock.json()["cantidad"] == "8.000"


async def test_rn_15_contar_10_sobre_10_no_crea_movimiento(
    cliente: httpx.AsyncClient, sesion: AsyncSession
) -> None:
    auth = await _sesion(cliente)
    p = await _producto(cliente, auth)
    await _cargar(cliente, auth, p["id"], "10")
    r = await _contar(cliente, auth, p["id"], "10")
    assert r.status_code == 200, r.text
    assert r.json()["diferencia"] == "0.000"
    assert r.json()["movimiento"] is None
    total = (
        await sesion.execute(sa.text("select count(*) from movimientos where tipo = 'ajuste'"))
    ).scalar_one()
    assert total == 0


async def test_rf_inv_013_contar_mas_de_lo_que_hay_crea_un_ajuste_positivo(
    cliente: httpx.AsyncClient,
) -> None:
    auth = await _sesion(cliente)
    p = await _producto(cliente, auth)
    await _cargar(cliente, auth, p["id"], "10")
    r = await _contar(cliente, auth, p["id"], "13")
    assert r.json()["diferencia"] == "3.000"
    assert r.json()["movimiento"]["direccion"] == 1


async def test_rf_inv_013_contar_cero_deja_el_stock_en_cero(cliente: httpx.AsyncClient) -> None:
    auth = await _sesion(cliente)
    p = await _producto(cliente, auth)
    await _cargar(cliente, auth, p["id"], "4")
    r = await _contar(cliente, auth, p["id"], "0")
    assert r.status_code == 200
    assert r.json()["movimiento"]["stock_resultante"] == "0.000"


async def test_rf_inv_010_el_conteo_exige_nota(cliente: httpx.AsyncClient) -> None:
    auth = await _sesion(cliente)
    p = await _producto(cliente, auth)
    r = await _contar(cliente, auth, p["id"], "3", nota=None)
    assert r.status_code == 422
    assert r.json()["error"]["code"] == "NOTA_OBLIGATORIA"


async def test_rn_07_la_cantidad_contada_respeta_la_unidad(cliente: httpx.AsyncClient) -> None:
    auth = await _sesion(cliente)
    p = await _producto(cliente, auth)
    r = await _contar(cliente, auth, p["id"], "2.5")
    assert r.status_code == 422
    assert r.json()["error"]["code"] == "CANTIDAD_INVALIDA_PARA_UNIDAD"
    assert (await _contar(cliente, auth, p["id"], "-1")).status_code == 422
    assert (await _contar(cliente, auth, p["id"], 3)).status_code == 422


async def test_rf_inv_011_el_conteo_es_idempotente_por_clave(cliente: httpx.AsyncClient) -> None:
    auth = await _sesion(cliente)
    p = await _producto(cliente, auth)
    await _cargar(cliente, auth, p["id"], "10")
    clave = _clave()
    a = await cliente.post(
        f"/api/v1/productos/{p['id']}/conteo",
        json={"cantidad_contada": "8", "nota": "x"},
        headers=auth | clave,
    )
    b = await cliente.post(
        f"/api/v1/productos/{p['id']}/conteo",
        json={"cantidad_contada": "8", "nota": "x"},
        headers=auth | clave,
    )
    assert a.json() == b.json()
    stock = await cliente.get(f"/api/v1/productos/{p['id']}/stock", headers=auth)
    assert stock.json()["cantidad"] == "8.000"


async def test_rf_cat_011_un_producto_archivado_no_se_cuenta(cliente: httpx.AsyncClient) -> None:
    auth = await _sesion(cliente)
    p = await _producto(cliente, auth)
    await cliente.post(f"/api/v1/productos/{p['id']}/archivar", headers=auth)
    r = await _contar(cliente, auth, p["id"], "3")
    assert r.status_code == 409
    assert r.json()["error"]["code"] == "PRODUCTO_ARCHIVADO"
