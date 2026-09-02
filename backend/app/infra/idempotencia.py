"""Idempotency-Key para toda escritura de negocio (constitution.md §2, RN-20, RNF-06).

- Misma clave y mismo cuerpo: se devuelve la respuesta guardada, sin volver a ejecutar.
- Misma clave y cuerpo distinto: 409 CLAVE_IDEMPOTENCIA_REUTILIZADA.
- Petición en curso con la misma clave: 409 OPERACION_EN_CURSO.
- Si la operación falla, la fila se descarta: el reintento vuelve a ejecutar.
"""

import hashlib
from collections.abc import Awaitable, Callable
from typing import Annotated, Any

import sqlalchemy as sa
from fastapi import Header, Request
from pydantic import BaseModel
from sqlalchemy.dialects.postgresql import insert
from sqlalchemy.ext.asyncio import AsyncSession
from starlette.responses import JSONResponse

from app.api.deps import ContextoNegocio
from app.dominio import errores as err
from app.modelos.sistema import OperacionIdempotente

ClaveIdempotencia = Annotated[
    str,
    Header(
        alias="Idempotency-Key",
        min_length=1,
        max_length=255,
        description="Clave única por operación, generada por el cliente al confirmar.",
    ),
]


def _hash(endpoint: str, cuerpo: bytes) -> str:
    return hashlib.sha256(endpoint.encode() + b"\n" + cuerpo).hexdigest()


async def ejecutar_idempotente(
    sesion: AsyncSession,
    contexto: ContextoNegocio,
    clave: str,
    request: Request,
    status_exito: int,
    operacion: Callable[[], Awaitable[BaseModel]],
) -> JSONResponse:
    endpoint = f"{request.method} {request.url.path}"
    hash_peticion = _hash(endpoint, await request.body())

    async with sesion.begin():
        insertado = (
            await sesion.execute(
                insert(OperacionIdempotente)
                .values(
                    negocio_id=contexto.negocio_id,
                    clave=clave,
                    endpoint=endpoint,
                    hash_peticion=hash_peticion,
                    estado="en_curso",
                )
                .on_conflict_do_nothing(index_elements=["negocio_id", "clave"])
                .returning(OperacionIdempotente.id)
            )
        ).scalar_one_or_none()
        if insertado is None:
            existente = (
                await sesion.execute(
                    sa.select(OperacionIdempotente).where(
                        OperacionIdempotente.negocio_id == contexto.negocio_id,
                        OperacionIdempotente.clave == clave,
                    )
                )
            ).scalar_one()
            if existente.estado == "en_curso":
                raise err.Conflicto(
                    "OPERACION_EN_CURSO",
                    "Esa operación todavía se está procesando. Espera un momento y reintenta.",
                )
            if existente.hash_peticion != hash_peticion:
                raise err.Conflicto(
                    "CLAVE_IDEMPOTENCIA_REUTILIZADA",
                    "Esa clave ya se usó para otra operación distinta.",
                    {"endpoint": existente.endpoint},
                )
            assert existente.status_http is not None
            return JSONResponse(status_code=existente.status_http, content=existente.respuesta)
        operacion_id = insertado

    try:
        resultado = await operacion()
    except BaseException:
        async with sesion.begin():
            await sesion.execute(
                sa.delete(OperacionIdempotente).where(OperacionIdempotente.id == operacion_id)
            )
        raise

    contenido: dict[str, Any] = resultado.model_dump(mode="json")
    async with sesion.begin():
        await sesion.execute(
            sa.update(OperacionIdempotente)
            .where(OperacionIdempotente.id == operacion_id)
            .values(estado="completada", status_http=status_exito, respuesta=contenido)
        )
    return JSONResponse(status_code=status_exito, content=contenido)
