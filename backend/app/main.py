from fastapi import APIRouter, FastAPI

from app.api.errores import registrar_manejadores
from app.api.v1 import auth
from app.config import obtener_ajustes
from app.infra.logging import configurar_logging, middleware_request_id

ajustes = obtener_ajustes()
configurar_logging(json=ajustes.log_json)

app = FastAPI(title="Inventario API", version="1.0.0")
app.middleware("http")(middleware_request_id)
registrar_manejadores(app)

api_v1 = APIRouter(prefix="/api/v1")


@api_v1.get("/salud", tags=["sistema"])
async def salud() -> dict[str, str]:
    return {"estado": "ok"}


api_v1.include_router(auth.router)
app.include_router(api_v1)
