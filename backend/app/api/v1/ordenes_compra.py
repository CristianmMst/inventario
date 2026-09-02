import uuid
from datetime import date
from typing import Annotated

from fastapi import APIRouter, Depends, Query, status

from app.api.deps import Contexto, SesionDb, paginacion
from app.esquemas.compras import (
    CancelacionEntrada,
    CierreFaltanteEntrada,
    EstadoOrden,
    OrdenEdicion,
    OrdenNueva,
    OrdenSalida,
)
from app.infra.paginacion import Pagina, ParametrosPagina
from app.servicios import compras as servicio

router = APIRouter(prefix="/ordenes-compra", tags=["compras"])


@router.get("", response_model=Pagina[OrdenSalida])
async def listar(
    sesion: SesionDb,
    contexto: Contexto,
    pagina: Annotated[ParametrosPagina, Depends(paginacion)],
    proveedor_id: Annotated[uuid.UUID | None, Query()] = None,
    estado: Annotated[EstadoOrden | None, Query()] = None,
    desde: Annotated[date | None, Query(description="Fecha de creación, inclusive")] = None,
    hasta: Annotated[date | None, Query(description="Fecha de creación, inclusive")] = None,
) -> Pagina[OrdenSalida]:
    """Listado de órdenes con filtros por proveedor, estado y rango de fechas (RF-COM-013)."""
    return await servicio.listar(
        sesion,
        contexto.negocio_id,
        pagina,
        proveedor_id=proveedor_id,
        estado=estado,
        desde=desde,
        hasta=hasta,
    )


@router.post("", status_code=status.HTTP_201_CREATED, response_model=OrdenSalida)
async def crear(datos: OrdenNueva, sesion: SesionDb, contexto: Contexto) -> OrdenSalida:
    """Orden de compra en borrador (RF-COM-002). La cantidad pendiente se calcula por línea."""
    return await servicio.crear(sesion, contexto.negocio_id, datos)


@router.get("/{orden_id}", response_model=OrdenSalida)
async def ficha(orden_id: uuid.UUID, sesion: SesionDb, contexto: Contexto) -> OrdenSalida:
    return await servicio.obtener(sesion, contexto.negocio_id, orden_id)


@router.patch("/{orden_id}", response_model=OrdenSalida)
async def editar(
    orden_id: uuid.UUID, datos: OrdenEdicion, sesion: SesionDb, contexto: Contexto
) -> OrdenSalida:
    """Edita un borrador; en otro estado responde 409 ORDEN_NO_EDITABLE (RF-COM-003)."""
    return await servicio.editar(sesion, contexto.negocio_id, orden_id, datos)


@router.post("/{orden_id}/emitir", response_model=OrdenSalida)
async def emitir(orden_id: uuid.UUID, sesion: SesionDb, contexto: Contexto) -> OrdenSalida:
    """Borrador → emitida. Solo desde emitida o parcialmente recibida se recibe (RF-COM-003)."""
    return await servicio.emitir(sesion, contexto, orden_id)


@router.post("/{orden_id}/cancelar", response_model=OrdenSalida)
async def cancelar(
    orden_id: uuid.UUID, datos: CancelacionEntrada, sesion: SesionDb, contexto: Contexto
) -> OrdenSalida:
    """Cancela una orden sin recepciones indicando el motivo (RF-COM-010). Con recepciones
    responde 409 y sugiere cerrar con faltante."""
    return await servicio.cancelar(sesion, contexto.negocio_id, orden_id, datos)


@router.post("/{orden_id}/cerrar-con-faltante", response_model=OrdenSalida)
async def cerrar_con_faltante(
    orden_id: uuid.UUID, datos: CierreFaltanteEntrada, sesion: SesionDb, contexto: Contexto
) -> OrdenSalida:
    """Cierra una orden parcialmente recibida indicando el motivo del faltante; ya no admite
    recepciones (RF-COM-008)."""
    return await servicio.cerrar_con_faltante(sesion, contexto, orden_id, datos)
