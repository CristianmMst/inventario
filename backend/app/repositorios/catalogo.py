import sqlalchemy as sa
from sqlalchemy.ext.asyncio import AsyncSession

from app.modelos.catalogo import UnidadMedida


class RepositorioUnidades:
    """Catálogo global: es la única tabla de negocio sin `negocio_id`, por ser semilla."""

    def __init__(self, sesion: AsyncSession) -> None:
        self._s = sesion

    async def listar(self, limite: int, despues_de_orden: int | None) -> list[UnidadMedida]:
        consulta = sa.select(UnidadMedida).order_by(UnidadMedida.orden).limit(limite)
        if despues_de_orden is not None:
            consulta = consulta.where(UnidadMedida.orden > despues_de_orden)
        return list((await self._s.execute(consulta)).scalars())

    async def por_codigo(self, codigo: str) -> UnidadMedida | None:
        return await self._s.get(UnidadMedida, codigo)
