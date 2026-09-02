"""Formato único de error (constitution.md §3) — RNF-07, RNF-12."""

import httpx
import pytest
from fastapi import APIRouter
from pydantic import BaseModel

from app.dominio import errores as err
from app.main import app

_router = APIRouter(prefix="/api/v1/_prueba")


class _Cuerpo(BaseModel):
    nombre: str
    cantidad: int


@_router.post("/valida")
async def _valida(cuerpo: _Cuerpo) -> dict[str, str]:
    return {"ok": cuerpo.nombre}


@_router.get("/no-encontrado")
async def _no_encontrado() -> None:
    raise err.NoEncontrado("PRODUCTO_NO_ENCONTRADO", "El producto no existe.", {"id": "x"})


@_router.get("/conflicto")
async def _conflicto() -> None:
    raise err.Conflicto("STOCK_INSUFICIENTE", "Solo hay 2 unidades.", {"disponible": "2.000"})


@_router.get("/sin-credencial")
async def _sin_credencial() -> None:
    raise err.NoAutenticado("CREDENCIAL_REQUERIDA", "Debes iniciar sesión.")


@_router.get("/sin-permiso")
async def _sin_permiso() -> None:
    raise err.SinPermiso("SIN_PERMISO", "No tienes permiso para esta acción.")


@_router.get("/semantico")
async def _semantico() -> None:
    raise err.ValidacionInvalida("CANTIDAD_INVALIDA_PARA_UNIDAD", "La cantidad debe ser entera.")


app.include_router(_router)


def _cliente() -> httpx.AsyncClient:
    transporte = httpx.ASGITransport(app=app, raise_app_exceptions=False)
    return httpx.AsyncClient(transport=transporte, base_url="http://test")


def _es_formato_unico(cuerpo: dict) -> bool:
    error = cuerpo["error"]
    return (
        set(cuerpo) == {"error"}
        and set(error) == {"code", "message", "details"}
        and isinstance(error["code"], str)
        and error["code"] == error["code"].upper()
        and isinstance(error["message"], str)
        and isinstance(error["details"], dict)
    )


@pytest.mark.parametrize(
    ("ruta", "status", "code"),
    [
        ("/api/v1/_prueba/no-encontrado", 404, "PRODUCTO_NO_ENCONTRADO"),
        ("/api/v1/_prueba/conflicto", 409, "STOCK_INSUFICIENTE"),
        ("/api/v1/_prueba/sin-credencial", 401, "CREDENCIAL_REQUERIDA"),
        ("/api/v1/_prueba/sin-permiso", 403, "SIN_PERMISO"),
        ("/api/v1/_prueba/semantico", 422, "CANTIDAD_INVALIDA_PARA_UNIDAD"),
    ],
)
async def test_rnf_07_error_de_dominio_se_traduce_al_formato_unico(
    ruta: str, status: int, code: str
) -> None:
    async with _cliente() as cliente:
        respuesta = await cliente.get(ruta)
    assert respuesta.status_code == status
    assert _es_formato_unico(respuesta.json())
    assert respuesta.json()["error"]["code"] == code


async def test_rnf_07_conflicto_conserva_los_details_del_dominio() -> None:
    async with _cliente() as cliente:
        respuesta = await cliente.get("/api/v1/_prueba/conflicto")
    assert respuesta.json()["error"]["details"] == {"disponible": "2.000"}


async def test_rnf_07_payload_malformado_responde_400() -> None:
    async with _cliente() as cliente:
        respuesta = await cliente.post(
            "/api/v1/_prueba/valida",
            content=b"{esto no es json",
            headers={"Content-Type": "application/json"},
        )
    assert respuesta.status_code == 400
    assert _es_formato_unico(respuesta.json())
    assert respuesta.json()["error"]["code"] == "PAYLOAD_MALFORMADO"


async def test_rnf_07_validacion_de_campos_responde_422_senalando_el_campo() -> None:
    async with _cliente() as cliente:
        respuesta = await cliente.post("/api/v1/_prueba/valida", json={"cantidad": "tres"})
    assert respuesta.status_code == 422
    cuerpo = respuesta.json()
    assert _es_formato_unico(cuerpo)
    assert cuerpo["error"]["code"] == "VALIDACION"
    campos = {c["campo"] for c in cuerpo["error"]["details"]["campos"]}
    assert campos == {"nombre", "cantidad"}


async def test_rnf_12_ruta_inexistente_responde_404_en_formato_unico() -> None:
    async with _cliente() as cliente:
        respuesta = await cliente.get("/api/v1/no-existe")
    assert respuesta.status_code == 404
    assert _es_formato_unico(respuesta.json())
    assert respuesta.json()["error"]["code"] == "RECURSO_NO_ENCONTRADO"


def test_rnf_12_los_errores_de_dominio_no_conocen_http() -> None:
    import inspect

    fuente = inspect.getsource(err)
    assert "fastapi" not in fuente.lower()
    assert "starlette" not in fuente.lower()
    assert "status_code" not in fuente
