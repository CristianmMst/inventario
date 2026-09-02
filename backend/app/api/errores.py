"""Traduce excepciones al formato único de error de constitution.md §3.

`{"error": {"code": "...", "message": "...", "details": {...}}}`
"""

from typing import Any

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from starlette.exceptions import HTTPException as StarletteHTTPException
from starlette.responses import JSONResponse

from app.dominio import errores as err

_STATUS_POR_TIPO: dict[type[err.ErrorDominio], int] = {
    err.NoEncontrado: 404,
    err.Conflicto: 409,
    err.ValidacionInvalida: 422,
    err.NoAutenticado: 401,
    err.SinPermiso: 403,
}

_CODIGO_POR_STATUS: dict[int, tuple[str, str]] = {
    400: ("PAYLOAD_MALFORMADO", "La petición no se pudo interpretar."),
    401: ("CREDENCIAL_REQUERIDA", "Debes iniciar sesión."),
    403: ("SIN_PERMISO", "No tienes permiso para esta acción."),
    404: ("RECURSO_NO_ENCONTRADO", "Lo que buscas no existe."),
    405: ("METODO_NO_PERMITIDO", "Esa operación no está disponible aquí."),
    409: ("CONFLICTO", "La operación choca con el estado actual."),
    422: ("VALIDACION", "Revisa los datos enviados."),
    429: ("LIMITE_DE_TASA", "Demasiadas peticiones. Espera un momento."),
}


def respuesta_error(
    status: int, code: str, message: str, details: dict[str, Any] | None = None
) -> JSONResponse:
    return JSONResponse(
        status_code=status,
        content={"error": {"code": code, "message": message, "details": details or {}}},
    )


def _status_de(excepcion: err.ErrorDominio) -> int:
    for tipo, status in _STATUS_POR_TIPO.items():
        if isinstance(excepcion, tipo):
            return status
    return 500


async def _manejar_dominio(_: Request, exc: Exception) -> JSONResponse:
    assert isinstance(exc, err.ErrorDominio)
    return respuesta_error(_status_de(exc), exc.code, exc.message, exc.details)


def _campo_de(error: dict[str, Any]) -> str:
    partes = [str(p) for p in error.get("loc", ()) if p not in ("body", "query", "path", "header")]
    return ".".join(partes) or "cuerpo"


async def _manejar_validacion(_: Request, exc: Exception) -> JSONResponse:
    assert isinstance(exc, RequestValidationError)
    errores = exc.errors()
    if any(e.get("type") in ("json_invalid", "json_type") for e in errores):
        code, message = _CODIGO_POR_STATUS[400]
        return respuesta_error(400, code, message)
    campos = [{"campo": _campo_de(e), "mensaje": e.get("msg", "")} for e in errores]
    code, message = _CODIGO_POR_STATUS[422]
    return respuesta_error(422, code, message, {"campos": campos})


async def _manejar_http(_: Request, exc: Exception) -> JSONResponse:
    assert isinstance(exc, StarletteHTTPException)
    code, message = _CODIGO_POR_STATUS.get(
        exc.status_code, ("ERROR_HTTP", "La petición no se pudo completar.")
    )
    return respuesta_error(exc.status_code, code, message)


def registrar_manejadores(app: FastAPI) -> None:
    app.add_exception_handler(err.ErrorDominio, _manejar_dominio)
    app.add_exception_handler(RequestValidationError, _manejar_validacion)
    app.add_exception_handler(StarletteHTTPException, _manejar_http)
