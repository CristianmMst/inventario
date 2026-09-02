"""RF-AUT-005: emisión, listado y revocación de credenciales de servicio."""

import secrets
import uuid
from datetime import UTC, datetime

from sqlalchemy.ext.asyncio import AsyncSession

from app.dominio import errores as err
from app.esquemas.api_keys import ApiKeyCreada, ApiKeyNueva, ApiKeySalida
from app.infra import seguridad
from app.infra.paginacion import Pagina, ParametrosPagina, decodificar_cursor, paginar
from app.modelos.identidad import ApiKey
from app.repositorios.api_keys import RepositorioApiKeys

PREFIJO_CLAVE = "inv"


def _salida(clave: ApiKey) -> ApiKeySalida:
    return ApiKeySalida(
        id=clave.id,
        nombre=clave.nombre,
        prefijo=clave.prefijo,
        created_at=clave.created_at,
        ultimo_uso_en=clave.ultimo_uso_en,
        revocado_en=clave.revocado_en,
    )


def _no_encontrada() -> err.NoEncontrado:
    return err.NoEncontrado("API_KEY_NO_ENCONTRADA", "Esa credencial de servicio no existe.")


async def emitir(sesion: AsyncSession, negocio_id: uuid.UUID, datos: ApiKeyNueva) -> ApiKeyCreada:
    repo = RepositorioApiKeys(sesion)
    prefijo = secrets.token_hex(4)  # 8 caracteres
    secreto = secrets.token_urlsafe(32)
    clave = ApiKey(
        negocio_id=negocio_id,
        nombre=datos.nombre,
        prefijo=prefijo,
        secreto_hash=seguridad.hash_secreto(secreto),
    )
    async with sesion.begin():
        repo.guardar(clave)
        await sesion.flush()
        await sesion.refresh(clave)
    return ApiKeyCreada(**_salida(clave).model_dump(), clave=f"{PREFIJO_CLAVE}_{prefijo}_{secreto}")


async def listar(
    sesion: AsyncSession, negocio_id: uuid.UUID, pagina: ParametrosPagina
) -> Pagina[ApiKeySalida]:
    repo = RepositorioApiKeys(sesion)
    despues_de = uuid.UUID(decodificar_cursor(pagina.cursor)["id"]) if pagina.cursor else None
    filas = await repo.listar(negocio_id, pagina.limit + 1, despues_de)
    return paginar([_salida(f) for f in filas], pagina.limit, clave_de=lambda f: {"id": str(f.id)})


async def revocar(sesion: AsyncSession, negocio_id: uuid.UUID, clave_id: uuid.UUID) -> None:
    repo = RepositorioApiKeys(sesion)
    async with sesion.begin():
        clave = await repo.por_id(negocio_id, clave_id)
        if clave is None:
            raise _no_encontrada()
        if clave.revocado_en is None:
            clave.revocado_en = datetime.now(UTC)
