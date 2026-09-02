import uuid
from typing import Annotated

from fastapi import APIRouter, Depends, status

from app.api.deps import Contexto, SesionDb, paginacion
from app.esquemas.catalogo import CategoriaEdicion, CategoriaNueva, CategoriaSalida
from app.infra.paginacion import Pagina, ParametrosPagina
from app.servicios import categorias as servicio

router = APIRouter(prefix="/categorias", tags=["catalogo"])


@router.get("", response_model=Pagina[CategoriaSalida])
async def listar(
    sesion: SesionDb,
    contexto: Contexto,
    pagina: Annotated[ParametrosPagina, Depends(paginacion)],
) -> Pagina[CategoriaSalida]:
    return await servicio.listar(sesion, contexto.negocio_id, pagina)


@router.post("", status_code=status.HTTP_201_CREATED, response_model=CategoriaSalida)
async def crear(datos: CategoriaNueva, sesion: SesionDb, contexto: Contexto) -> CategoriaSalida:
    """Alta de categoría. El nombre es único en el negocio (RF-CAT-005)."""
    return await servicio.crear(sesion, contexto.negocio_id, datos)


@router.patch("/{categoria_id}", response_model=CategoriaSalida)
async def editar(
    categoria_id: uuid.UUID, datos: CategoriaEdicion, sesion: SesionDb, contexto: Contexto
) -> CategoriaSalida:
    return await servicio.editar(sesion, contexto.negocio_id, categoria_id, datos)
