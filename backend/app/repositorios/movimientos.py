import uuid

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
