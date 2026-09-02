"""Emisión y consulta de eventos de dominio (RF-INT-001, RF-INT-004, RN-21).

`emitir` escribe la fila en la sesión en curso: el llamador está dentro de la transacción del
hecho, así que si el hecho se revierte, el evento también. En v1 solo se persisten y se
consultan; la entrega por webhook es otro proceso que leerá `eventos` por `secuencia`.
"""

import uuid
from collections.abc import Callable
from datetime import datetime
from typing import Any

import sqlalchemy as sa
from sqlalchemy.ext.asyncio import AsyncSession

from app.api.deps import ContextoNegocio
from app.dominio import eventos as ev
from app.esquemas.eventos import AutorEvento, EventoSalida
from app.infra.paginacion import Pagina, ParametrosPagina, decodificar_cursor, paginar
from app.modelos.eventos import Evento

Constructor = Callable[..., ev.Evento]


def autor_de(contexto: ContextoNegocio) -> ev.Autor:
    return ev.Autor(tipo=contexto.autor.tipo, id=contexto.autor.id, nombre=contexto.autor.nombre)


async def emitir(
    sesion: AsyncSession, contexto: ContextoNegocio, constructor: Constructor, **campos: Any
) -> ev.Evento:
    """Construye el evento con el catálogo del dominio y lo inserta en la misma transacción."""
    evento = constructor(contexto.negocio_id, autor_de(contexto), **campos)
    sesion.add(
        Evento(
            id=evento.id,
            negocio_id=evento.business_id,
            tipo=evento.tipo,
            version=evento.version,
            ocurrido_en=evento.ocurrido_en,
            autor_tipo=evento.autor.tipo,
            autor_id=evento.autor.id,
            autor_nombre=evento.autor.nombre,
            payload=evento.payload,
        )
    )
    await sesion.flush()
    return evento


def a_salida(e: Evento) -> EventoSalida:
    return EventoSalida(
        id=e.id,
        secuencia=e.secuencia,
        tipo=e.tipo,
        version=e.version,
        business_id=e.negocio_id,
        ocurrido_en=e.ocurrido_en,
        autor=AutorEvento(tipo=e.autor_tipo, id=e.autor_id, nombre=e.autor_nombre),  # type: ignore[arg-type]
        payload=e.payload,
    )


async def listar(
    sesion: AsyncSession,
    negocio_id: uuid.UUID,
    pagina: ParametrosPagina,
    *,
    desde_secuencia: int | None,
    tipo: str | None,
    desde: datetime | None,
    hasta: datetime | None,
) -> Pagina[EventoSalida]:
    """RF-INT-004: en orden de ocurrencia (secuencia ascendente). `desde_secuencia` es
    exclusivo: el consumidor pasa la última que ya procesó."""
    despues = desde_secuencia
    if pagina.cursor:
        despues = int(decodificar_cursor(pagina.cursor)["s"])
    consulta = (
        sa.select(Evento)
        .where(Evento.negocio_id == negocio_id)
        .order_by(Evento.secuencia)
        .limit(pagina.limit + 1)
    )
    if despues is not None:
        consulta = consulta.where(Evento.secuencia > despues)
    if tipo is not None:
        consulta = consulta.where(Evento.tipo == tipo)
    if desde is not None:
        consulta = consulta.where(Evento.ocurrido_en >= desde)
    if hasta is not None:
        consulta = consulta.where(Evento.ocurrido_en < hasta)
    async with sesion.begin():
        filas = list((await sesion.execute(consulta)).scalars())
    return paginar(
        [a_salida(f) for f in filas], pagina.limit, clave_de=lambda s: {"s": s.secuencia}
    )
