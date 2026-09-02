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
