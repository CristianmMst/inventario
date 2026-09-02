"""Instantánea de stock (RF-INV-003, RF-INV-004, RN-01).

No existe ninguna operación que fije el stock a un valor: `actualizar` solo lo llama el
servicio de movimientos, en la misma transacción que el movimiento y con la fila bloqueada.
"""

import uuid
from dataclasses import dataclass
from datetime import datetime
from decimal import Decimal

import sqlalchemy as sa
from sqlalchemy.dialects.postgresql import insert
from sqlalchemy.ext.asyncio import AsyncSession

from app.dominio.tipos import Cantidad
from app.modelos.inventario import Movimiento, StockProducto


@dataclass(frozen=True, slots=True)
class Discrepancia:
    producto_id: uuid.UUID
    instantanea: Decimal
    suma: Decimal


class RepositorioStock:
    def __init__(self, sesion: AsyncSession) -> None:
        self._s = sesion

    async def bloquear(self, negocio_id: uuid.UUID, producto_id: uuid.UUID) -> Cantidad:
        """`SELECT … FOR UPDATE` sobre la fila del producto: serializa a los competidores de ese
        producto y solo de ese (plan.md §4.4). Crea la fila si aún no existe."""
        await self._s.execute(
            insert(StockProducto)
            .values(producto_id=producto_id, negocio_id=negocio_id)
            .on_conflict_do_nothing(index_elements=["producto_id"])
        )
        cantidad = (
            await self._s.execute(
                sa.select(StockProducto.cantidad)
                .where(
                    StockProducto.producto_id == producto_id,
                    StockProducto.negocio_id == negocio_id,
                )
                .with_for_update()
            )
        ).scalar_one()
        return Cantidad(cantidad)

    async def actual(self, negocio_id: uuid.UUID, producto_id: uuid.UUID) -> Cantidad:
        cantidad = (
            await self._s.execute(
                sa.select(StockProducto.cantidad).where(
                    StockProducto.producto_id == producto_id,
                    StockProducto.negocio_id == negocio_id,
                )
            )
        ).scalar_one_or_none()
        return Cantidad(cantidad if cantidad is not None else Decimal(0))

    async def bajo_minimo_anterior(self, negocio_id: uuid.UUID, producto_id: uuid.UUID) -> bool:
        return (
            await self._s.execute(
                sa.select(StockProducto.bajo_minimo).where(
                    StockProducto.producto_id == producto_id,
                    StockProducto.negocio_id == negocio_id,
                )
            )
        ).scalar_one_or_none() is True

    async def actualizar(
        self,
        negocio_id: uuid.UUID,
        producto_id: uuid.UUID,
        cantidad: Cantidad,
        *,
        stock_minimo: Decimal | None,
    ) -> bool:
        """Escribe la instantánea y devuelve el nuevo estado `bajo_minimo`."""
        bajo = stock_minimo is not None and cantidad.valor <= stock_minimo
        await self._s.execute(
            sa.update(StockProducto)
            .where(StockProducto.producto_id == producto_id, StockProducto.negocio_id == negocio_id)
            .values(cantidad=cantidad.valor, bajo_minimo=bajo, actualizado_en=sa.func.now())
        )
        return bajo

    async def reconciliar(self, negocio_id: uuid.UUID) -> list[Discrepancia]:
        """Compara la instantánea con la suma real de movimientos. Debe devolver `[]`."""
        suma = (
            sa.select(
                Movimiento.producto_id,
                sa.func.coalesce(sa.func.sum(Movimiento.cantidad * Movimiento.direccion), 0).label(
                    "suma"
                ),
            )
            .where(Movimiento.negocio_id == negocio_id)
            .group_by(Movimiento.producto_id)
            .subquery()
        )
        consulta = (
            sa.select(
                StockProducto.producto_id,
                StockProducto.cantidad,
                sa.func.coalesce(suma.c.suma, 0).label("suma"),
            )
            .outerjoin(suma, suma.c.producto_id == StockProducto.producto_id)
            .where(StockProducto.negocio_id == negocio_id)
            .where(StockProducto.cantidad != sa.func.coalesce(suma.c.suma, 0))
        )
        filas = (await self._s.execute(consulta)).all()
        return [
            Discrepancia(
                producto_id=f.producto_id,
                instantanea=Decimal(f.cantidad),
                suma=Decimal(f.suma).quantize(Decimal("0.001")),
            )
            for f in filas
        ]

    async def actuales(
        self, negocio_id: uuid.UUID, producto_ids: list[uuid.UUID]
    ) -> dict[uuid.UUID, Cantidad]:
        """Stock de varios productos de una vez; los que no tienen fila están en cero."""
        if not producto_ids:
            return {}
        filas = (
            await self._s.execute(
                sa.select(StockProducto.producto_id, StockProducto.cantidad).where(
                    StockProducto.negocio_id == negocio_id,
                    StockProducto.producto_id.in_(producto_ids),
                )
            )
        ).all()
        encontrados = {f.producto_id: Cantidad(f.cantidad) for f in filas}
        return {pid: encontrados.get(pid, Cantidad(Decimal(0))) for pid in producto_ids}

    async def detalle(
        self, negocio_id: uuid.UUID, producto_id: uuid.UUID
    ) -> tuple[Cantidad, datetime | None]:
        fila = (
            await self._s.execute(
                sa.select(StockProducto.cantidad, StockProducto.actualizado_en).where(
                    StockProducto.producto_id == producto_id,
                    StockProducto.negocio_id == negocio_id,
                )
            )
        ).one_or_none()
        if fila is None:
            return Cantidad(Decimal(0)), None
        return Cantidad(fila.cantidad), fila.actualizado_en
