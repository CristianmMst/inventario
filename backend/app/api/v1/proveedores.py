import uuid
from typing import Annotated, Literal

from fastapi import APIRouter, Depends, Query, status

from app.api.deps import Contexto, SesionDb, paginacion
from app.esquemas.compras import ProveedorEdicion, ProveedorNuevo, ProveedorSalida
from app.infra.paginacion import Pagina, ParametrosPagina
from app.servicios import proveedores as servicio

router = APIRouter(prefix="/proveedores", tags=["compras"])


@router.get("", response_model=Pagina[ProveedorSalida])
async def listar(
    sesion: SesionDb,
    contexto: Contexto,
    pagina: Annotated[ParametrosPagina, Depends(paginacion)],
    estado: Annotated[Literal["activo", "archivado", "todos"], Query()] = "activo",
) -> Pagina[ProveedorSalida]:
    return await servicio.listar(sesion, contexto.negocio_id, pagina, estado=estado)


@router.post("", status_code=status.HTTP_201_CREATED, response_model=ProveedorSalida)
async def crear(datos: ProveedorNuevo, sesion: SesionDb, contexto: Contexto) -> ProveedorSalida:
    """Alta de proveedor; solo el nombre es obligatorio (RF-COM-001)."""
    return await servicio.crear(sesion, contexto.negocio_id, datos)


@router.get("/{proveedor_id}", response_model=ProveedorSalida)
async def ficha(proveedor_id: uuid.UUID, sesion: SesionDb, contexto: Contexto) -> ProveedorSalida:
    return await servicio.obtener(sesion, contexto.negocio_id, proveedor_id)


@router.patch("/{proveedor_id}", response_model=ProveedorSalida)
async def editar(
    proveedor_id: uuid.UUID, datos: ProveedorEdicion, sesion: SesionDb, contexto: Contexto
) -> ProveedorSalida:
    return await servicio.editar(sesion, contexto.negocio_id, proveedor_id, datos)


@router.delete("/{proveedor_id}", status_code=status.HTTP_204_NO_CONTENT)
async def eliminar(proveedor_id: uuid.UUID, sesion: SesionDb, contexto: Contexto) -> None:
    """Borra un proveedor sin documentos. Con documentos responde 409 y sugiere archivar (RN-17)."""
    await servicio.eliminar(sesion, contexto.negocio_id, proveedor_id)


@router.post("/{proveedor_id}/archivar", response_model=ProveedorSalida)
async def archivar(
    proveedor_id: uuid.UUID, sesion: SesionDb, contexto: Contexto
) -> ProveedorSalida:
    """Archiva: deja de ser seleccionable pero sigue nombrado en su historial (RN-17)."""
    return await servicio.archivar(sesion, contexto.negocio_id, proveedor_id)


@router.post("/{proveedor_id}/desarchivar", response_model=ProveedorSalida)
async def desarchivar(
    proveedor_id: uuid.UUID, sesion: SesionDb, contexto: Contexto
) -> ProveedorSalida:
    return await servicio.desarchivar(sesion, contexto.negocio_id, proveedor_id)
