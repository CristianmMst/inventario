import uuid

from fastapi import APIRouter, status

from app.api.deps import Contexto, SesionDb
from app.esquemas.catalogo import ProductoEdicion, ProductoNuevo, ProductoSalida
from app.servicios import productos as servicio

router = APIRouter(prefix="/productos", tags=["catalogo"])


@router.post("", status_code=status.HTTP_201_CREATED, response_model=ProductoSalida)
async def crear(datos: ProductoNuevo, sesion: SesionDb, contexto: Contexto) -> ProductoSalida:
    """Alta de producto. Nombre y unidad obligatorios; el SKU se genera si falta (RF-CAT-001)."""
    return await servicio.crear(sesion, contexto.negocio_id, datos)


@router.get("/{producto_id}", response_model=ProductoSalida)
async def ficha(producto_id: uuid.UUID, sesion: SesionDb, contexto: Contexto) -> ProductoSalida:
    return await servicio.obtener(sesion, contexto.negocio_id, producto_id)


@router.patch("/{producto_id}", response_model=ProductoSalida)
async def editar(
    producto_id: uuid.UUID, datos: ProductoEdicion, sesion: SesionDb, contexto: Contexto
) -> ProductoSalida:
    """Edición parcial. Costo y precio sobrescriben el valor vigente (RF-CAT-010)."""
    return await servicio.editar(sesion, contexto.negocio_id, producto_id, datos)
