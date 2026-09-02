import uuid
from typing import Annotated

from fastapi import APIRouter, Depends, status

from app.api.deps import Contexto, SesionDb, paginacion
from app.esquemas.api_keys import ApiKeyCreada, ApiKeyNueva, ApiKeySalida
from app.infra.paginacion import Pagina, ParametrosPagina
from app.servicios import api_keys as servicio

router = APIRouter(prefix="/api-keys", tags=["api-keys"])


@router.post("", status_code=status.HTTP_201_CREATED, response_model=ApiKeyCreada)
async def crear(datos: ApiKeyNueva, sesion: SesionDb, contexto: Contexto) -> ApiKeyCreada:
    """Emite una credencial de servicio. La clave completa solo se muestra aquí (RF-AUT-005)."""
    return await servicio.emitir(sesion, contexto.negocio_id, datos)


@router.get("", response_model=Pagina[ApiKeySalida])
async def listar(
    sesion: SesionDb,
    contexto: Contexto,
    pagina: Annotated[ParametrosPagina, Depends(paginacion)],
) -> Pagina[ApiKeySalida]:
    return await servicio.listar(sesion, contexto.negocio_id, pagina)


@router.delete("/{clave_id}", status_code=status.HTTP_204_NO_CONTENT)
async def revocar(clave_id: uuid.UUID, sesion: SesionDb, contexto: Contexto) -> None:
    """Revoca la credencial. Sigue apareciendo en el listado con su fecha de revocación."""
    await servicio.revocar(sesion, contexto.negocio_id, clave_id)
