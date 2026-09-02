import uuid
from typing import Annotated, Literal

from fastapi import APIRouter, Depends, Query, status

from app.api.deps import Contexto, SesionDb, paginacion
from app.esquemas.catalogo import ProductoEdicion, ProductoNuevo, ProductoSalida
from app.infra.paginacion import Pagina, ParametrosPagina
from app.servicios import productos as servicio

router = APIRouter(prefix="/productos", tags=["catalogo"])


@router.post("", status_code=status.HTTP_201_CREATED, response_model=ProductoSalida)
async def crear(datos: ProductoNuevo, sesion: SesionDb, contexto: Contexto) -> ProductoSalida:
    """Alta de producto. Nombre y unidad obligatorios; el SKU se genera si falta (RF-CAT-001)."""
    return await servicio.crear(sesion, contexto.negocio_id, datos)


@router.get("", response_model=Pagina[ProductoSalida])
async def listar(
    sesion: SesionDb,
    contexto: Contexto,
    pagina: Annotated[ParametrosPagina, Depends(paginacion)],
    categoria_id: Annotated[uuid.UUID | None, Query()] = None,
    estado: Annotated[Literal["activo", "archivado", "todos"], Query()] = "activo",
) -> Pagina[ProductoSalida]:
    """Listado paginado con filtros por categoría y estado (RF-CAT-014)."""
    return await servicio.listar(
        sesion, contexto.negocio_id, pagina, categoria_id=categoria_id, estado=estado
    )


@router.get("/buscar", response_model=Pagina[ProductoSalida])
async def buscar(
    q: Annotated[str, Query(min_length=1, max_length=200, description="Texto a buscar")],
    sesion: SesionDb,
    contexto: Contexto,
    pagina: Annotated[ParametrosPagina, Depends(paginacion)],
) -> Pagina[ProductoSalida]:
    """Búsqueda por texto sobre nombre, SKU y categoría, insensible a mayúsculas y tildes, con
    coincidencia parcial y resultados por relevancia (RF-CAT-007)."""
    return await servicio.buscar(sesion, contexto.negocio_id, q, pagina)


@router.get(
    "/por-codigo/{codigo}",
    response_model=ProductoSalida,
    responses={
        404: {
            "description": "Ningún producto tiene ese código. `details.codigo` trae el código "
            "consultado para precargar el alta (RF-CAT-009).",
            "content": {
                "application/json": {
                    "example": {
                        "error": {
                            "code": "PRODUCTO_NO_ENCONTRADO",
                            "message": "Ningún producto tiene el código 7701234567890.",
                            "details": {"codigo": "7701234567890"},
                        }
                    }
                }
            },
        }
    },
)
async def por_codigo(codigo: str, sesion: SesionDb, contexto: Contexto) -> ProductoSalida:
    """Escaneo: búsqueda por código de barras exacto (RF-CAT-008)."""
    return await servicio.por_codigo_barras(sesion, contexto.negocio_id, codigo)


@router.get("/{producto_id}", response_model=ProductoSalida)
async def ficha(producto_id: uuid.UUID, sesion: SesionDb, contexto: Contexto) -> ProductoSalida:
    return await servicio.obtener(sesion, contexto.negocio_id, producto_id)


@router.patch("/{producto_id}", response_model=ProductoSalida)
async def editar(
    producto_id: uuid.UUID, datos: ProductoEdicion, sesion: SesionDb, contexto: Contexto
) -> ProductoSalida:
    """Edición parcial. Costo y precio sobrescriben el valor vigente (RF-CAT-010)."""
    return await servicio.editar(sesion, contexto.negocio_id, producto_id, datos)


@router.post("/{producto_id}/archivar", response_model=ProductoSalida)
async def archivar(producto_id: uuid.UUID, sesion: SesionDb, contexto: Contexto) -> ProductoSalida:
    """Archiva el producto: sale de las búsquedas de operación y no admite movimientos, pero
    conserva su historial (RF-CAT-011, RN-17). Sustituye al borrado, que no existe."""
    return await servicio.archivar(sesion, contexto.negocio_id, producto_id)


@router.post("/{producto_id}/desarchivar", response_model=ProductoSalida)
async def desarchivar(
    producto_id: uuid.UUID, sesion: SesionDb, contexto: Contexto
) -> ProductoSalida:
    return await servicio.desarchivar(sesion, contexto.negocio_id, producto_id)
