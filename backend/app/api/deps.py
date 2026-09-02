"""Dependencias de FastAPI compartidas por todos los routers."""

import uuid
from dataclasses import dataclass
from typing import Annotated, Literal

import jwt
from fastapi import Depends, Header, Query
from sqlalchemy.ext.asyncio import AsyncSession

from app.dominio import errores as err
from app.infra import seguridad
from app.infra.db import sesion as sesion_db
from app.infra.paginacion import LIMITE_MAXIMO, LIMITE_POR_DEFECTO, ParametrosPagina
from app.repositorios.api_keys import RepositorioApiKeys

SesionDb = Annotated[AsyncSession, Depends(sesion_db)]

PREFIJO_API_KEY = "inv"


def paginacion(
    cursor: Annotated[str | None, Query(description="Cursor opaco de la página anterior")] = None,
    limit: Annotated[int, Query(ge=1, le=LIMITE_MAXIMO)] = LIMITE_POR_DEFECTO,
) -> ParametrosPagina:
    return ParametrosPagina(cursor=cursor, limit=limit)


@dataclass(frozen=True, slots=True)
class Autor:
    tipo: Literal["usuario", "servicio"]
    id: uuid.UUID
    nombre: str


@dataclass(frozen=True, slots=True)
class ContextoNegocio:
    """Todo lo que un caso de uso necesita saber de quién llama: el negocio y el autor.
    Los repositorios reciben `negocio_id` siempre (RN-19)."""

    negocio_id: uuid.UUID
    autor: Autor


def _invalida() -> err.NoAutenticado:
    return err.NoAutenticado(
        "CREDENCIAL_INVALIDA", "La credencial no es válida o caducó. Vuelve a iniciar sesión."
    )


def _contexto_desde_jwt(authorization: str) -> ContextoNegocio:
    esquema, _, token = authorization.partition(" ")
    if esquema.lower() != "bearer" or not token:
        raise _invalida()
    try:
        carga = seguridad.decodificar_token_acceso(token)
    except jwt.PyJWTError as e:
        raise _invalida() from e
    return ContextoNegocio(
        negocio_id=uuid.UUID(carga["biz"]),
        autor=Autor(
            tipo="usuario",
            id=uuid.UUID(carga["sub"]),
            nombre=str(carga.get("nombre", "")),
        ),
    )


async def _contexto_desde_api_key(sesion: AsyncSession, clave: str) -> ContextoNegocio:
    """`inv_<prefijo8>_<secreto>`: el prefijo localiza la fila, el secreto se verifica con
    Argon2id. Ningún fallo revela si el prefijo existe (RNF-12)."""
    partes = clave.split("_", 2)
    if len(partes) != 3 or partes[0] != PREFIJO_API_KEY or len(partes[1]) != 8:
        raise _invalida()
    repo = RepositorioApiKeys(sesion)
    async with sesion.begin():
        fila = await repo.por_prefijo(partes[1])
        if (
            fila is None
            or fila.revocado_en is not None
            or not seguridad.verificar_secreto(partes[2], fila.secreto_hash)
        ):
            raise _invalida()
        await repo.marcar_uso(fila.id)
        return ContextoNegocio(
            negocio_id=fila.negocio_id, autor=Autor(tipo="servicio", id=fila.id, nombre=fila.nombre)
        )


async def contexto_actual(
    sesion: SesionDb,
    authorization: Annotated[str | None, Header()] = None,
    x_api_key: Annotated[str | None, Header()] = None,
) -> ContextoNegocio:
    """Resuelve la credencial de la petición: `Authorization: Bearer` (usuario) o `X-API-Key`
    (servicio). Ambas autorizan lo mismo dentro de su negocio (RF-AUT-005, RF-INT-008).
    Sin credencial: 401 CREDENCIAL_REQUERIDA; inválida o caducada: 401 CREDENCIAL_INVALIDA."""
    if authorization:
        return _contexto_desde_jwt(authorization)
    if x_api_key:
        return await _contexto_desde_api_key(sesion, x_api_key)
    raise err.NoAutenticado("CREDENCIAL_REQUERIDA", "Debes iniciar sesión.")


Contexto = Annotated[ContextoNegocio, Depends(contexto_actual)]
