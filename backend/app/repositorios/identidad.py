import uuid

import sqlalchemy as sa
from sqlalchemy.ext.asyncio import AsyncSession

from app.modelos.identidad import Membresia, Negocio, Usuario


class RepositorioIdentidad:
    def __init__(self, sesion: AsyncSession) -> None:
        self._s = sesion

    async def usuario_por_email(self, email: str) -> Usuario | None:
        return (
            await self._s.execute(sa.select(Usuario).where(Usuario.email == email))
        ).scalar_one_or_none()

    async def crear_usuario_con_negocio(
        self, usuario: Usuario, negocio: Negocio, rol: str
    ) -> Membresia:
        self._s.add_all([usuario, negocio])
        await self._s.flush()
        membresia = Membresia(usuario_id=usuario.id, negocio_id=negocio.id, rol=rol)
        self._s.add(membresia)
        await self._s.flush()
        return membresia

    async def negocio_de_usuario(self, usuario_id: uuid.UUID) -> Negocio:
        """v1 es mono-negocio: el usuario tiene exactamente una membresía."""
        return (
            await self._s.execute(
                sa.select(Negocio)
                .join(Membresia, Membresia.negocio_id == Negocio.id)
                .where(Membresia.usuario_id == usuario_id)
                .order_by(Membresia.created_at)
                .limit(1)
            )
        ).scalar_one()
