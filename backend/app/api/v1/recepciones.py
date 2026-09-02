import uuid
from datetime import date
from typing import Annotated, Literal

from fastapi import APIRouter, Depends, Query, Request, status
from starlette.responses import JSONResponse

from app.api.deps import Contexto, SesionDb, paginacion
from app.esquemas.compras import (
    ConfirmacionRecepcion,
    RecepcionEdicion,
    RecepcionNueva,
    RecepcionSalida,
)
from app.infra.idempotencia import ClaveIdempotencia, ejecutar_idempotente
from app.infra.paginacion import Pagina, ParametrosPagina
from app.servicios import recepciones as servicio

router = APIRouter(prefix="/recepciones", tags=["compras"])


@router.get("", response_model=Pagina[RecepcionSalida])
async def listar(
    sesion: SesionDb,
    contexto: Contexto,
    pagina: Annotated[ParametrosPagina, Depends(paginacion)],
    proveedor_id: Annotated[uuid.UUID | None, Query()] = None,
    orden_id: Annotated[uuid.UUID | None, Query()] = None,
    estado: Annotated[Literal["borrador", "confirmada", "corregida"] | None, Query()] = None,
    desde: Annotated[date | None, Query(description="Fecha de recepción, inclusive")] = None,
    hasta: Annotated[date | None, Query(description="Fecha de recepción, inclusive")] = None,
) -> Pagina[RecepcionSalida]:
    """Listado de recepciones con filtros por proveedor, orden, estado y fechas (RF-COM-013)."""
    return await servicio.listar(
        sesion,
        contexto.negocio_id,
        pagina,
        proveedor_id=proveedor_id,
        orden_id=orden_id,
        estado=estado,
        desde=desde,
        hasta=hasta,
    )


@router.post("", status_code=status.HTTP_201_CREATED, response_model=RecepcionSalida)
async def crear(datos: RecepcionNueva, sesion: SesionDb, contexto: Contexto) -> RecepcionSalida:
    """Recepción en borrador, con o sin orden de compra (RF-COM-004, RF-COM-005)."""
    return await servicio.crear(sesion, contexto.negocio_id, datos)


@router.get("/{recepcion_id}", response_model=RecepcionSalida)
async def ficha(recepcion_id: uuid.UUID, sesion: SesionDb, contexto: Contexto) -> RecepcionSalida:
    return await servicio.obtener(sesion, contexto.negocio_id, recepcion_id)


@router.patch("/{recepcion_id}", response_model=RecepcionSalida)
async def editar(
    recepcion_id: uuid.UUID, datos: RecepcionEdicion, sesion: SesionDb, contexto: Contexto
) -> RecepcionSalida:
    """Edita un borrador. Una confirmada responde 409 RECEPCION_INMUTABLE (RF-COM-012)."""
    return await servicio.editar(sesion, contexto.negocio_id, recepcion_id, datos)


@router.post(
    "/{recepcion_id}/confirmar",
    response_model=RecepcionSalida,
    responses={
        409: {
            "description": "RECEPCION_INMUTABLE (ya confirmada), EXCESO_SOBRE_ORDEN "
            "(RF-COM-009, reintenta con confirmar_exceso), ORDEN_NO_RECIBIBLE o "
            "PRODUCTO_ARCHIVADO. En todos los casos no entra ninguna línea (RN-13).",
            "content": {
                "application/json": {
                    "example": {
                        "error": {
                            "code": "EXCESO_SOBRE_ORDEN",
                            "message": "Estás recibiendo más de lo que pedía la orden OC-000001.",
                            "details": {
                                "orden_id": "…",
                                "lineas": [
                                    {
                                        "producto_id": "…",
                                        "pendiente": "40.000",
                                        "recibido": "60.000",
                                    }
                                ],
                                "confirmar_con": "confirmar_exceso",
                            },
                        }
                    }
                }
            },
        }
    },
)
async def confirmar(
    recepcion_id: uuid.UUID,
    datos: ConfirmacionRecepcion,
    request: Request,
    sesion: SesionDb,
    contexto: Contexto,
    clave: ClaveIdempotencia,
) -> JSONResponse:
    """Confirma la recepción: genera atómicamente una entrada por línea, congela costos y
    actualiza el costo actual y el estado de la orden (RF-COM-006..011). Exige Idempotency-Key."""

    async def operacion() -> RecepcionSalida:
        return await servicio.confirmar(sesion, contexto, recepcion_id, datos)

    return await ejecutar_idempotente(
        sesion, contexto, clave, request, status.HTTP_200_OK, operacion
    )
