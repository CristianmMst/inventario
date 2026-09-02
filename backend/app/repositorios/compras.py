import uuid
from datetime import date
from decimal import Decimal

import sqlalchemy as sa
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.orm import selectinload

from app.modelos.compras import OrdenCompra, OrdenCompraLinea, Proveedor
from app.modelos.identidad import Negocio


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
        hay_ordenes = (
            await self._s.execute(
                sa.select(sa.literal(True)).where(OrdenCompra.proveedor_id == proveedor_id).limit(1)
            )
        ).scalar_one_or_none()
        return hay_ordenes is True


class RepositorioOrdenes:
    def __init__(self, sesion: AsyncSession) -> None:
        self._s = sesion

    def _cargada(self, consulta: sa.Select[tuple[OrdenCompra]]) -> sa.Select[tuple[OrdenCompra]]:
        return consulta.options(
            selectinload(OrdenCompra.proveedor),
            selectinload(OrdenCompra.lineas).selectinload(OrdenCompraLinea.producto),
        ).execution_options(populate_existing=True)

    def guardar(self, orden: OrdenCompra) -> None:
        self._s.add(orden)

    async def siguiente_secuencia(self, negocio_id: uuid.UUID) -> int:
        """Bloquea la fila del negocio para que dos órdenes simultáneas no compartan número."""
        await self._s.execute(
            sa.select(Negocio.id).where(Negocio.id == negocio_id).with_for_update()
        )
        actual = (
            await self._s.execute(
                sa.select(sa.func.coalesce(sa.func.max(OrdenCompra.secuencia), 0)).where(
                    OrdenCompra.negocio_id == negocio_id
                )
            )
        ).scalar_one()
        return int(actual) + 1

    async def por_id(self, negocio_id: uuid.UUID, orden_id: uuid.UUID) -> OrdenCompra | None:
        return (
            await self._s.execute(
                self._cargada(
                    sa.select(OrdenCompra).where(
                        OrdenCompra.negocio_id == negocio_id, OrdenCompra.id == orden_id
                    )
                )
            )
        ).scalar_one_or_none()

    async def listar(
        self,
        negocio_id: uuid.UUID,
        *,
        proveedor_id: uuid.UUID | None = None,
        estado: str | None = None,
        desde: date | None = None,
        hasta: date | None = None,
        limite: int,
        despues_de: tuple[int] | None,
    ) -> list[OrdenCompra]:
        consulta = (
            sa.select(OrdenCompra)
            .where(OrdenCompra.negocio_id == negocio_id)
            .order_by(OrdenCompra.secuencia.desc())
            .limit(limite)
        )
        if proveedor_id is not None:
            consulta = consulta.where(OrdenCompra.proveedor_id == proveedor_id)
        if estado is not None:
            consulta = consulta.where(OrdenCompra.estado == estado)
        if desde is not None:
            consulta = consulta.where(sa.cast(OrdenCompra.created_at, sa.Date) >= desde)
        if hasta is not None:
            consulta = consulta.where(sa.cast(OrdenCompra.created_at, sa.Date) <= hasta)
        if despues_de is not None:
            consulta = consulta.where(OrdenCompra.secuencia < despues_de[0])
        return list((await self._s.execute(self._cargada(consulta))).scalars())

    async def recibido_por_linea(self, orden_id: uuid.UUID) -> dict[uuid.UUID, Decimal]:
        """Σ recibido por línea desde las recepciones confirmadas. Llega con T-039/T-040."""
        return {}
