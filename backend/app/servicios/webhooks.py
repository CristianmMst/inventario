"""RF-INT-005: suscripciones de webhook. Se persisten; la entrega (RF-INT-006/007) es contrato
documentado y no se implementa en v1: ningún módulo hace peticiones salientes."""

import uuid

import sqlalchemy as sa
from sqlalchemy.ext.asyncio import AsyncSession

from app.dominio import errores as err
from app.dominio.eventos import CATALOGO
from app.esquemas.webhooks import SuscripcionNueva, SuscripcionSalida
from app.infra import seguridad
from app.infra.paginacion import Pagina, ParametrosPagina, decodificar_cursor, paginar
from app.modelos.eventos import SuscripcionWebhook

COMODIN = "*"


def _salida(s: SuscripcionWebhook) -> SuscripcionSalida:
    return SuscripcionSalida(
        id=s.id,
        url=s.url,
        tipos=list(s.tipos),
        activa=s.activa,
        descripcion=s.descripcion,
        created_at=s.created_at,
    )


def _tipos_validos(tipos: list[str]) -> list[str]:
    if COMODIN in tipos:
        return [COMODIN]
    desconocidos = sorted(t for t in tipos if t not in CATALOGO)
    if desconocidos:
        raise err.ValidacionInvalida(
            "TIPO_DE_EVENTO_DESCONOCIDO",
            "Alguno de los tipos no existe en el catálogo de eventos.",
            {"tipos_desconocidos": desconocidos, "tipos_validos": sorted(CATALOGO)},
        )
    return sorted(set(tipos))


async def crear(
    sesion: AsyncSession, negocio_id: uuid.UUID, datos: SuscripcionNueva
) -> SuscripcionSalida:
    suscripcion = SuscripcionWebhook(
        negocio_id=negocio_id,
        url=str(datos.url),
        tipos=_tipos_validos(datos.tipos),
        secreto_hash=seguridad.hash_secreto(datos.secreto),
        descripcion=datos.descripcion,
    )
    async with sesion.begin():
        sesion.add(suscripcion)
        await sesion.flush()
        await sesion.refresh(suscripcion)
    return _salida(suscripcion)


async def listar(
    sesion: AsyncSession, negocio_id: uuid.UUID, pagina: ParametrosPagina
) -> Pagina[SuscripcionSalida]:
    despues = None
    if pagina.cursor:
        c = decodificar_cursor(pagina.cursor)
        despues = (str(c["c"]), uuid.UUID(str(c["id"])))
    consulta = (
        sa.select(SuscripcionWebhook)
        .where(SuscripcionWebhook.negocio_id == negocio_id)
        .order_by(SuscripcionWebhook.created_at, SuscripcionWebhook.id)
        .limit(pagina.limit + 1)
    )
    if despues is not None:
        consulta = consulta.where(
            sa.tuple_(SuscripcionWebhook.created_at, SuscripcionWebhook.id)
            > sa.tuple_(sa.cast(despues[0], sa.DateTime(timezone=True)), sa.literal(despues[1]))
        )
    async with sesion.begin():
        filas = list((await sesion.execute(consulta)).scalars())
    return paginar(
        [_salida(f) for f in filas],
        pagina.limit,
        clave_de=lambda s: {"c": s.created_at.isoformat(), "id": str(s.id)},
    )


async def eliminar(sesion: AsyncSession, negocio_id: uuid.UUID, suscripcion_id: uuid.UUID) -> None:
    async with sesion.begin():
        suscripcion = (
            await sesion.execute(
                sa.select(SuscripcionWebhook).where(
                    SuscripcionWebhook.negocio_id == negocio_id,
                    SuscripcionWebhook.id == suscripcion_id,
                )
            )
        ).scalar_one_or_none()
        if suscripcion is None:
            raise err.NoEncontrado("SUSCRIPCION_NO_ENCONTRADA", "Esa suscripción no existe.")
        await sesion.delete(suscripcion)
