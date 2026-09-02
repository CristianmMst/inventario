import uuid

from fastapi import APIRouter, status

from app.api.deps import Contexto, SesionDb
from app.esquemas.catalogo import CodigoBarrasNuevo, ProductoSalida
from app.servicios import productos as servicio

router = APIRouter(prefix="/productos/{producto_id}/codigos-barras", tags=["catalogo"])


@router.post("", status_code=status.HTTP_201_CREATED, response_model=ProductoSalida)
async def agregar(
    producto_id: uuid.UUID, datos: CodigoBarrasNuevo, sesion: SesionDb, contexto: Contexto
) -> ProductoSalida:
    """Asigna un código de barras al producto. Un código ya usado responde 409 indicando a qué
    producto pertenece (RF-CAT-003, RN-05)."""
    return await servicio.agregar_codigo(sesion, contexto.negocio_id, producto_id, datos.codigo)


@router.delete("/{codigo}", status_code=status.HTTP_204_NO_CONTENT)
async def quitar(producto_id: uuid.UUID, codigo: str, sesion: SesionDb, contexto: Contexto) -> None:
    await servicio.quitar_codigo(sesion, contexto.negocio_id, producto_id, codigo)
