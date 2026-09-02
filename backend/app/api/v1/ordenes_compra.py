import uuid
from datetime import date
from typing import Annotated

from fastapi import APIRouter, Depends, Query, status

from app.api.deps import Contexto, SesionDb, paginacion
from app.esquemas.compras import EstadoOrden, OrdenEdicion, OrdenNueva, OrdenSalida
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
