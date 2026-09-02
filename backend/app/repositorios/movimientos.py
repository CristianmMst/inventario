import uuid
from datetime import datetime

import sqlalchemy as sa
from sqlalchemy.ext.asyncio import AsyncSession

from app.modelos.inventario import Movimiento


class RepositorioMovimientos:
    def __init__(self, sesion: AsyncSession) -> None:
        self._s = sesion

    def guardar(self, movimiento: Movimiento) -> None:
        self._s.add(movimiento)

    async def por_id(self, negocio_id: uuid.UUID, movimiento_id: uuid.UUID) -> Movimiento | None:
        return (
            await self._s.execute(
                sa.select(Movimiento).where(
                    Movimiento.negocio_id == negocio_id, Movimiento.id == movimiento_id
                )
            )
        ).scalar_one_or_none()

    async def marcar_anulado(self, movimiento_id: uuid.UUID) -> int:
        """Fija `anulado_en` si aún es nulo. Devuelve filas afectadas (1 o 0)."""
        resultado = await self._s.execute(
            sa.update(Movimiento)
            .where(Movimiento.id == movimiento_id, Movimiento.anulado_en.is_(None))
            .values(anulado_en=sa.func.now())
        )
        return int(resultado.rowcount or 0)

    async def listar(
        self,
        negocio_id: uuid.UUID,
        *,
        producto_id: uuid.UUID | None = None,
        tipo: str | None = None,
        desde: datetime | None = None,
        hasta: datetime | None = None,
        limite: int,
        despues_de: tuple[datetime, uuid.UUID] | None,
    ) -> list[Movimiento]:
        """Orden cronológico inverso con cursor (ocurrido_en, id): usa
        ix_movimientos_producto_ocurrido cuando se filtra por producto (RF-INV-012)."""
        consulta = (
            sa.select(Movimiento)
            .where(Movimiento.negocio_id == negocio_id)
            .order_by(Movimiento.ocurrido_en.desc(), Movimiento.id.desc())
            .limit(limite)
        )
        if producto_id is not None:
            consulta = consulta.where(Movimiento.producto_id == producto_id)
        if tipo is not None:
            consulta = consulta.where(Movimiento.tipo == tipo)
        if desde is not None:
            consulta = consulta.where(Movimiento.ocurrido_en >= desde)
        if hasta is not None:
            consulta = consulta.where(Movimiento.ocurrido_en < hasta)
        if despues_de is not None:
            momento, mid = despues_de
            consulta = consulta.where(
                sa.tuple_(Movimiento.ocurrido_en, Movimiento.id) < sa.tuple_(momento, mid)
            )
        return list((await self._s.execute(consulta)).scalars())
