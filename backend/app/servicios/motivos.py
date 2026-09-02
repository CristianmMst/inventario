"""RF-INV-010: motivo de una lista cerrada por tipo; la nota es obligatoria cuando toca."""

import sqlalchemy as sa
from sqlalchemy.ext.asyncio import AsyncSession

from app.dominio import errores as err
from app.dominio.movimientos import TipoMovimiento
from app.modelos.inventario import MotivoMovimiento

# Ajustes, mermas, movimientos forzados y anulaciones exigen nota aunque el motivo no.
TIPOS_CON_NOTA_OBLIGATORIA = frozenset(
    {TipoMovimiento.AJUSTE, TipoMovimiento.MERMA, TipoMovimiento.CONTRAMOVIMIENTO}
)


async def motivos_de(sesion: AsyncSession, tipo: TipoMovimiento | None) -> list[MotivoMovimiento]:
    consulta = sa.select(MotivoMovimiento).order_by(
        MotivoMovimiento.tipo_movimiento, MotivoMovimiento.orden, MotivoMovimiento.codigo
    )
    if tipo is not None:
        consulta = consulta.where(MotivoMovimiento.tipo_movimiento == tipo.value)
    return list((await sesion.execute(consulta)).scalars())


def _nota_vacia(nota: str | None) -> bool:
    return nota is None or not nota.strip()


async def validar_motivo(
    sesion: AsyncSession,
    tipo: TipoMovimiento,
    motivo: str,
    nota: str | None,
    *,
    forzado: bool = False,
) -> MotivoMovimiento:
    """Devuelve el motivo si es válido para el tipo. La semilla trae `otro` en cada tipo."""
    validos = {m.codigo: m for m in await motivos_de(sesion, tipo)}
    elegido = validos.get(motivo)
    if elegido is None:
        raise err.ValidacionInvalida(
            "MOTIVO_INVALIDO",
            f"«{motivo}» no es un motivo válido para un movimiento de tipo {tipo.value}.",
            {"motivo": motivo, "tipo": tipo.value, "motivos_validos": sorted(validos)},
        )
    exige_nota = elegido.exige_nota or tipo in TIPOS_CON_NOTA_OBLIGATORIA or forzado
    if exige_nota and _nota_vacia(nota):
        raise err.ValidacionInvalida(
            "NOTA_OBLIGATORIA",
            "Este movimiento necesita una nota que explique el motivo.",
            {"motivo": motivo, "tipo": tipo.value, "forzado": forzado},
        )
    return elegido
