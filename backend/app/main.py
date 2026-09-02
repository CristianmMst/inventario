from fastapi import APIRouter, FastAPI

from app.api.errores import registrar_manejadores
from app.api.v1 import (
    api_keys,
    auth,
    categorias,
    codigos_barras,
    conteos,
    imagenes,
    motivos,
    movimientos,
    negocio,
    ordenes_compra,
    productos,
    proveedores,
    recepciones,
    unidades,
)
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
api_v1.include_router(api_keys.router)
api_v1.include_router(negocio.router)
api_v1.include_router(unidades.router)
api_v1.include_router(categorias.router)
api_v1.include_router(productos.router)
api_v1.include_router(codigos_barras.router)
api_v1.include_router(motivos.router)
api_v1.include_router(movimientos.router)
api_v1.include_router(movimientos.router_stock)
api_v1.include_router(conteos.router)
api_v1.include_router(proveedores.router)
api_v1.include_router(ordenes_compra.router)
api_v1.include_router(recepciones.router)
api_v1.include_router(imagenes.router)
app.include_router(api_v1)
