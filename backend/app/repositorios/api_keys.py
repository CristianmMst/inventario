import uuid

import sqlalchemy as sa
from sqlalchemy.ext.asyncio import AsyncSession

from app.modelos.identidad import ApiKey


class RepositorioApiKeys:
    """Toda consulta lleva `negocio_id` (RN-19), salvo la búsqueda por prefijo al autenticar,
    que es la que descubre el negocio."""

    def __init__(self, sesion: AsyncSession) -> None:
        self._s = sesion

    def guardar(self, clave: ApiKey) -> None:
        self._s.add(clave)

    async def listar(
        self, negocio_id: uuid.UUID, limite: int, despues_de: uuid.UUID | None
    ) -> list[ApiKey]:
        consulta = (
            sa.select(ApiKey)
            .where(ApiKey.negocio_id == negocio_id)
            .order_by(ApiKey.created_at.desc(), ApiKey.id.desc())
            .limit(limite)
        )
        if despues_de is not None:
            anterior = sa.select(ApiKey.created_at).where(ApiKey.id == despues_de).scalar_subquery()
            consulta = consulta.where(
                sa.tuple_(ApiKey.created_at, ApiKey.id) < sa.tuple_(anterior, despues_de)
            )
        return list((await self._s.execute(consulta)).scalars())

    async def por_id(self, negocio_id: uuid.UUID, clave_id: uuid.UUID) -> ApiKey | None:
        return (
            await self._s.execute(
                sa.select(ApiKey).where(ApiKey.negocio_id == negocio_id, ApiKey.id == clave_id)
            )
        ).scalar_one_or_none()

    async def por_prefijo(self, prefijo: str) -> ApiKey | None:
        return (
            await self._s.execute(sa.select(ApiKey).where(ApiKey.prefijo == prefijo))
        ).scalar_one_or_none()

    async def marcar_uso(self, clave_id: uuid.UUID) -> None:
        await self._s.execute(
            sa.update(ApiKey).where(ApiKey.id == clave_id).values(ultimo_uso_en=sa.func.now())
        )
