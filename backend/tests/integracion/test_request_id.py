import httpx
import structlog
from fastapi import APIRouter

from app.main import app

_router = APIRouter()


@_router.get("/api/v1/_prueba/explota")
async def _explota() -> None:
    raise RuntimeError("fallo provocado con detalle interno: SELECT * FROM secretos")


app.include_router(_router)


def _cliente() -> httpx.AsyncClient:
    transporte = httpx.ASGITransport(app=app, raise_app_exceptions=False)
    return httpx.AsyncClient(transport=transporte, base_url="http://test")


async def test_rnf_12_un_500_lleva_x_request_id_y_no_expone_traza() -> None:
    async with _cliente() as cliente:
        respuesta = await cliente.get("/api/v1/_prueba/explota")
    assert respuesta.status_code == 500
    assert respuesta.headers.get("X-Request-Id")
    cuerpo = respuesta.text
    assert "Traceback" not in cuerpo
    assert "RuntimeError" not in cuerpo
    assert "SELECT" not in cuerpo
    assert respuesta.json()["error"]["code"] == "ERROR_INTERNO"
    assert respuesta.json()["error"]["details"]["request_id"] == respuesta.headers["X-Request-Id"]


async def test_rnf_07_el_x_request_id_del_cliente_se_conserva() -> None:
    async with _cliente() as cliente:
        respuesta = await cliente.get("/api/v1/salud", headers={"X-Request-Id": "abc-123"})
    assert respuesta.headers["X-Request-Id"] == "abc-123"


async def test_rnf_12_el_log_del_error_correlaciona_con_el_request_id() -> None:
    with structlog.testing.capture_logs() as registros:
        async with _cliente() as cliente:
            respuesta = await cliente.get("/api/v1/_prueba/explota")
    request_id = respuesta.headers["X-Request-Id"]
    errores = [r for r in registros if r.get("log_level") == "error"]
    assert errores, "no se registró ningún error en el log"
    assert all(r.get("request_id") == request_id for r in errores)
