import uuid

from fastapi import APIRouter, Request, status
from starlette.responses import JSONResponse

from app.api.deps import Contexto, SesionDb
from app.esquemas.inventario import MovimientoNuevo, MovimientoSalida, StockSalida
from app.infra.idempotencia import ClaveIdempotencia, ejecutar_idempotente
from app.repositorios.stock import RepositorioStock
from app.servicios import movimientos as servicio

router = APIRouter(prefix="/movimientos", tags=["inventario"])
router_stock = APIRouter(prefix="/productos", tags=["inventario"])

EJEMPLO_STOCK_INSUFICIENTE = {
    "error": {
        "code": "STOCK_INSUFICIENTE",
        "message": "Solo hay 2.000 de «Cuaderno 100 hojas» y pides 5.000.",
        "details": {
            "producto_id": "8b1c…",
            "solicitado": "5.000",
            "disponible": "2.000",
            "puede_forzar": True,
        },
    }
}


@router.post(
    "",
    status_code=status.HTTP_201_CREATED,
    response_model=MovimientoSalida,
    responses={
        409: {
            "description": "Stock insuficiente (RF-INV-005). `details.puede_forzar` indica si "
            "se admite el override de RF-INV-006.",
            "content": {"application/json": {"example": EJEMPLO_STOCK_INSUFICIENTE}},
        }
    },
)
async def registrar(
    datos: MovimientoNuevo,
    request: Request,
    sesion: SesionDb,
    contexto: Contexto,
    clave: ClaveIdempotencia,
) -> JSONResponse:
    """Registra un movimiento de inventario (RF-INV-001). Exige `Idempotency-Key`: reintentar
    con la misma clave devuelve el mismo movimiento, nunca un duplicado (RF-INV-011)."""

    async def operacion() -> MovimientoSalida:
        return await servicio.registrar(sesion, contexto, datos)

    return await ejecutar_idempotente(
        sesion, contexto, clave, request, status.HTTP_201_CREATED, operacion
    )


@router_stock.get("/{producto_id}/stock", response_model=StockSalida)
async def stock(producto_id: uuid.UUID, sesion: SesionDb, contexto: Contexto) -> StockSalida:
    """Stock actual del producto: siempre la suma de sus movimientos (RF-INV-003)."""
    async with sesion.begin():
        await servicio.producto_existente(sesion, contexto.negocio_id, producto_id)
        cantidad, actualizado_en = await RepositorioStock(sesion).detalle(
            contexto.negocio_id, producto_id
        )
    return StockSalida(
        producto_id=producto_id, cantidad=cantidad.a_api(), actualizado_en=actualizado_en
    )
