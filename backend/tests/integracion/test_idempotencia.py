"""RN-20 y RNF-06: Idempotency-Key en toda escritura de negocio (plan.md §4.3)."""

import itertools
import uuid

import httpx
import pytest
import sqlalchemy as sa
from fastapi import APIRouter, Request, status
from pydantic import BaseModel
from sqlalchemy.ext.asyncio import AsyncSession
from starlette.responses import Response

from app.api.deps import Contexto, SesionDb
from app.dominio import errores as err
from app.infra.idempotencia import ClaveIdempotencia, ejecutar_idempotente
from app.main import app
from tests import fabricas

_ejecuciones = itertools.count(1)
_router = APIRouter(prefix="/api/v1/_prueba")


class _Entrada(BaseModel):
    cantidad: int


class _Salida(BaseModel):
    ejecucion: int
    cantidad: int


@_router.post("/hecho", status_code=status.HTTP_201_CREATED)
async def _hecho(
    datos: _Entrada,
    request: Request,
    sesion: SesionDb,
    contexto: Contexto,
    clave: ClaveIdempotencia,
) -> Response:
    async def operacion() -> _Salida:
        if datos.cantidad < 0:
            raise err.Conflicto("NEGATIVO", "No.")
        return _Salida(ejecucion=next(_ejecuciones), cantidad=datos.cantidad)

    return await ejecutar_idempotente(
        sesion, contexto, clave, request, status.HTTP_201_CREATED, operacion
    )


app.include_router(_router)


async def _sesion(cliente: httpx.AsyncClient) -> dict[str, str]:
    cuerpo = {
        "email": fabricas.correo_unico(),
        "password": fabricas.CONTRASENA_VALIDA,
        "nombre": "Marta",
        "negocio": {"nombre": "P", "moneda_base": "COP", "zona_horaria": "UTC"},
    }
    r = await cliente.post("/api/v1/auth/registro", json=cuerpo)
    return {"Authorization": f"Bearer {r.json()['token_acceso']}"}


async def _post(
    cliente: httpx.AsyncClient, auth: dict[str, str], clave: str | None, cantidad: int
) -> httpx.Response:
    headers = dict(auth)
    if clave is not None:
        headers["Idempotency-Key"] = clave
    return await cliente.post("/api/v1/_prueba/hecho", json={"cantidad": cantidad}, headers=headers)


async def test_rn_20_misma_clave_y_mismo_cuerpo_devuelve_la_respuesta_guardada_sin_reejecutar(
    cliente: httpx.AsyncClient,
) -> None:
    auth = await _sesion(cliente)
    clave = str(uuid.uuid4())
    primera = await _post(cliente, auth, clave, 5)
    assert primera.status_code == 201, primera.text
    repeticiones = [await _post(cliente, auth, clave, 5) for _ in range(4)]
    for r in repeticiones:
        assert r.status_code == 201
        assert r.json() == primera.json()


async def test_rn_20_misma_clave_y_cuerpo_distinto_responde_409(cliente: httpx.AsyncClient) -> None:
    auth = await _sesion(cliente)
    clave = str(uuid.uuid4())
    await _post(cliente, auth, clave, 5)
    otra = await _post(cliente, auth, clave, 6)
    assert otra.status_code == 409
    assert otra.json()["error"]["code"] == "CLAVE_IDEMPOTENCIA_REUTILIZADA"


async def test_rnf_06_sin_idempotency_key_la_escritura_se_rechaza_con_422(
    cliente: httpx.AsyncClient,
) -> None:
    auth = await _sesion(cliente)
    respuesta = await _post(cliente, auth, None, 5)
    assert respuesta.status_code == 422
    campos = {c["campo"].lower() for c in respuesta.json()["error"]["details"]["campos"]}
    assert "idempotency-key" in campos


async def test_rn_20_la_clave_es_por_negocio(cliente: httpx.AsyncClient) -> None:
    a, b = await _sesion(cliente), await _sesion(cliente)
    clave = str(uuid.uuid4())
    en_a = await _post(cliente, a, clave, 5)
    en_b = await _post(cliente, b, clave, 5)
    assert en_a.status_code == en_b.status_code == 201
    assert en_a.json()["ejecucion"] != en_b.json()["ejecucion"]


async def test_rn_20_un_fallo_de_negocio_no_se_guarda_y_el_reintento_vuelve_a_ejecutar(
    cliente: httpx.AsyncClient, sesion: AsyncSession
) -> None:
    auth = await _sesion(cliente)
    clave = str(uuid.uuid4())
    fallo = await _post(cliente, auth, clave, -1)
    assert fallo.status_code == 409 and fallo.json()["error"]["code"] == "NEGATIVO"
    guardadas = (
        await sesion.execute(sa.text("select count(*) from operaciones_idempotentes"))
    ).scalar_one()
    assert guardadas == 0
    # El cliente corrige y reintenta con la misma clave: se ejecuta de verdad.
    assert (await _post(cliente, auth, clave, 1)).status_code == 201


async def test_rn_20_una_operacion_en_curso_con_la_misma_clave_responde_409(
    cliente: httpx.AsyncClient, sesion: AsyncSession
) -> None:
    auth = await _sesion(cliente)
    negocio_id = (await cliente.get("/api/v1/negocio", headers=auth)).json()["id"]
    clave = str(uuid.uuid4())
    await sesion.execute(
        sa.text(
            "insert into operaciones_idempotentes"
            " (negocio_id, clave, endpoint, hash_peticion, estado)"
            " values (:n, :c, 'POST /api/v1/_prueba/hecho', 'x', 'en_curso')"
        ),
        {"n": negocio_id, "c": clave},
    )
    await sesion.commit()
    respuesta = await _post(cliente, auth, clave, 5)
    assert respuesta.status_code == 409
    assert respuesta.json()["error"]["code"] == "OPERACION_EN_CURSO"


@pytest.mark.parametrize("clave", ["", "x" * 256])
async def test_rn_20_la_clave_tiene_longitud_acotada(
    cliente: httpx.AsyncClient, clave: str
) -> None:
    auth = await _sesion(cliente)
    assert (await _post(cliente, auth, clave, 5)).status_code == 422
