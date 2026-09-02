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

SesionDb = Annotated[AsyncSession, Depends(sesion_db)]


def paginacion(
    cursor: Annotated[str | None, Query(description="Cursor opaco de la página anterior")] = None,
    limit: Annotated[int, Query(ge=1, le=LIMITE_MAXIMO)] = LIMITE_POR_DEFECTO,
) -> ParametrosPagina:
    return ParametrosPagina(cursor=cursor, limit=limit)


@dataclass(frozen=True, slots=True)
class Autor:
    tipo: Literal["usuario", "servicio"]
    id: uuid.UUID


@dataclass(frozen=True, slots=True)
class ContextoNegocio:
    """Todo lo que un caso de uso necesita saber de quién llama: el negocio y el autor.
    Los repositorios reciben `negocio_id` siempre (RN-19)."""

    negocio_id: uuid.UUID
    autor: Autor


async def contexto_actual(
    authorization: Annotated[str | None, Header()] = None,
) -> ContextoNegocio:
    """Resuelve la credencial de la petición. Sin credencial: 401 CREDENCIAL_REQUERIDA;
    credencial inválida o caducada: 401 CREDENCIAL_INVALIDA."""
    if authorization:
        esquema, _, token = authorization.partition(" ")
        if esquema.lower() != "bearer" or not token:
            raise err.NoAutenticado("CREDENCIAL_INVALIDA", "La credencial no es válida.")
        try:
            carga = seguridad.decodificar_token_acceso(token)
        except jwt.PyJWTError as e:
            raise err.NoAutenticado(
                "CREDENCIAL_INVALIDA", "La sesión no es válida o caducó. Vuelve a iniciar sesión."
            ) from e
        return ContextoNegocio(
            negocio_id=uuid.UUID(carga["biz"]),
            autor=Autor(tipo="usuario", id=uuid.UUID(carga["sub"])),
        )
    raise err.NoAutenticado("CREDENCIAL_REQUERIDA", "Debes iniciar sesión.")


Contexto = Annotated[ContextoNegocio, Depends(contexto_actual)]
