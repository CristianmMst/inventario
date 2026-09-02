import uuid

import sqlalchemy as sa
from sqlalchemy.ext.asyncio import AsyncSession

from app.modelos.imagenes import Imagen


class RepositorioImagenes:
    def __init__(self, sesion: AsyncSession) -> None:
        self._s = sesion

    def guardar(self, imagen: Imagen) -> None:
        self._s.add(imagen)

    async def por_id(self, negocio_id: uuid.UUID, imagen_id: uuid.UUID) -> Imagen | None:
        return (
            await self._s.execute(
                sa.select(Imagen).where(Imagen.negocio_id == negocio_id, Imagen.id == imagen_id)
            )
        ).scalar_one_or_none()

    async def por_identificador(self, identificador: str) -> Imagen | None:
        """Lectura por URL firmada: el token ya autoriza; no hay negocio en la petición."""
        return (
            await self._s.execute(sa.select(Imagen).where(Imagen.identificador == identificador))
        ).scalar_one_or_none()

    async def borrar(self, imagen: Imagen) -> None:
        await self._s.delete(imagen)
