import uuid

import sqlalchemy as sa
from sqlalchemy.ext.asyncio import AsyncSession

from app.modelos.compras import Proveedor


class RepositorioProveedores:
    def __init__(self, sesion: AsyncSession) -> None:
        self._s = sesion

    def guardar(self, proveedor: Proveedor) -> None:
        self._s.add(proveedor)

    async def por_id(self, negocio_id: uuid.UUID, proveedor_id: uuid.UUID) -> Proveedor | None:
        return (
            await self._s.execute(
                sa.select(Proveedor).where(
                    Proveedor.negocio_id == negocio_id, Proveedor.id == proveedor_id
                )
            )
        ).scalar_one_or_none()

    async def listar(
        self,
        negocio_id: uuid.UUID,
        *,
        estado: str | None,
        limite: int,
        despues_de: tuple[str, uuid.UUID] | None,
    ) -> list[Proveedor]:
        consulta = (
            sa.select(Proveedor)
            .where(Proveedor.negocio_id == negocio_id)
            .order_by(Proveedor.nombre, Proveedor.id)
            .limit(limite)
        )
        if estado is not None:
            consulta = consulta.where(Proveedor.estado == estado)
        if despues_de is not None:
            nombre, pid = despues_de
            consulta = consulta.where(
                sa.tuple_(Proveedor.nombre, Proveedor.id) > sa.tuple_(nombre, pid)
            )
        return list((await self._s.execute(consulta)).scalars())

    async def borrar(self, proveedor: Proveedor) -> None:
        await self._s.delete(proveedor)

    async def tiene_documentos(self, proveedor_id: uuid.UUID) -> bool:
        """Órdenes, recepciones o facturas que lo nombran. Crece con cada tabla de H4 y H5."""
        return False
