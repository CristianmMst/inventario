"""Logging estructurado y correlación por `X-Request-Id` (RNF-07, RNF-12)."""

import logging
import uuid
from collections.abc import Awaitable, Callable

import structlog
from starlette.requests import Request
from starlette.responses import JSONResponse, Response

CABECERA_REQUEST_ID = "X-Request-Id"

log = structlog.get_logger("inventario")


def configurar_logging(*, json: bool = True) -> None:
    procesadores: list[structlog.types.Processor] = [
        structlog.contextvars.merge_contextvars,
        structlog.processors.add_log_level,
        structlog.processors.TimeStamper(fmt="iso", utc=True),
        structlog.processors.StackInfoRenderer(),
        structlog.processors.format_exc_info,
    ]
    render: structlog.types.Processor = (
        structlog.processors.JSONRenderer() if json else structlog.dev.ConsoleRenderer()
    )
    structlog.configure(
        processors=[*procesadores, render],
        wrapper_class=structlog.make_filtering_bound_logger(logging.INFO),
        logger_factory=structlog.PrintLoggerFactory(),
        cache_logger_on_first_use=False,
    )


def _cuerpo_error_interno(request_id: str) -> dict[str, object]:
    return {
        "error": {
            "code": "ERROR_INTERNO",
            "message": "Ocurrió un error en el servidor. Intenta de nuevo; si persiste, "
            "reporta el identificador indicado.",
            "details": {"request_id": request_id},
        }
    }


async def middleware_request_id(
    request: Request, call_next: Callable[[Request], Awaitable[Response]]
) -> Response:
    """Asigna o conserva el `X-Request-Id`, lo enlaza al log y lo devuelve en la respuesta.

    Ante una excepción no controlada, registra la traza en el log y responde un 500 con el
    formato único de error, sin exponer nada interno (RNF-12).
    """
    request_id = request.headers.get(CABECERA_REQUEST_ID) or str(uuid.uuid4())
    structlog.contextvars.clear_contextvars()
    structlog.contextvars.bind_contextvars(
        request_id=request_id, metodo=request.method, ruta=request.url.path
    )
    try:
        respuesta = await call_next(request)
    except Exception:
        log.exception("error_no_controlado", request_id=request_id)
        respuesta = JSONResponse(status_code=500, content=_cuerpo_error_interno(request_id))
    respuesta.headers[CABECERA_REQUEST_ID] = request_id
    return respuesta
