import uuid
from datetime import datetime
from typing import Annotated

from fastapi import APIRouter, Depends, Query, Request, status
from starlette.responses import JSONResponse

from app.api.deps import Contexto, SesionDb, paginacion
from app.dominio.movimientos import TipoMovimiento
from app.esquemas.inventario import (
    AnulacionEntrada,
    MovimientoNuevo,
    MovimientoSalida,
    StockSalida,
)
from app.infra.idempotencia import ClaveIdempotencia, ejecutar_idempotente
from app.infra.paginacion import Pagina, ParametrosPagina
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


@router.get("", response_model=Pagina[MovimientoSalida])
async def listar(
    sesion: SesionDb,
    contexto: Contexto,
    pagina: Annotated[ParametrosPagina, Depends(paginacion)],
    producto_id: Annotated[uuid.UUID | None, Query()] = None,
    tipo: Annotated[TipoMovimiento | None, Query()] = None,
    desde: Annotated[datetime | None, Query(description="Inclusive, ISO 8601")] = None,
    hasta: Annotated[datetime | None, Query(description="Exclusivo, ISO 8601")] = None,
) -> Pagina[MovimientoSalida]:
    """Consulta de movimientos con filtros, en orden cronológico inverso (RF-INV-002)."""
    return await servicio.listar(
        sesion,
        contexto.negocio_id,
        pagina,
        producto_id=producto_id,
        tipo=tipo,
        desde=desde,
        hasta=hasta,
    )


@router_stock.get("/{producto_id}/movimientos", response_model=Pagina[MovimientoSalida])
async def historial(
    producto_id: uuid.UUID,
    sesion: SesionDb,
    contexto: Contexto,
    pagina: Annotated[ParametrosPagina, Depends(paginacion)],
) -> Pagina[MovimientoSalida]:
    """Historial del producto, paginado, con el stock resultante tras cada movimiento y la marca
    de anulado (RF-INV-012, RF-REP-004). Los generados por una recepción traen su línea."""
    return await servicio.historial_producto(sesion, contexto.negocio_id, producto_id, pagina)


@router.get("/{movimiento_id}", response_model=MovimientoSalida)
async def detalle(
    movimiento_id: uuid.UUID, sesion: SesionDb, contexto: Contexto
) -> MovimientoSalida:
    return await servicio.obtener(sesion, contexto.negocio_id, movimiento_id)


@router.post(
    "/{movimiento_id}/anular", status_code=status.HTTP_201_CREATED, response_model=MovimientoSalida
)
async def anular(
    movimiento_id: uuid.UUID,
    datos: AnulacionEntrada,
    request: Request,
    sesion: SesionDb,
    contexto: Contexto,
    clave: ClaveIdempotencia,
) -> JSONResponse:
    """Anula un movimiento creando su contramovimiento (RF-INV-008). Exige nota. Un anulado no
    se vuelve a anular y un contramovimiento no se anula (409)."""

    async def operacion() -> MovimientoSalida:
        return await servicio.anular(sesion, contexto, movimiento_id, datos)

    return await ejecutar_idempotente(
        sesion, contexto, clave, request, status.HTTP_201_CREATED, operacion
    )
