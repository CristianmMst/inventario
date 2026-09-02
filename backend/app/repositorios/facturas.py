import uuid
from datetime import date
from decimal import Decimal

import sqlalchemy as sa
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.orm import selectinload

from app.modelos.compras import Recepcion
from app.modelos.facturas import Factura, FacturaImagen, FacturaRecepcion


class RepositorioFacturas:
    def __init__(self, sesion: AsyncSession) -> None:
        self._s = sesion

    def _cargada(self, consulta: sa.Select[tuple[Factura]]) -> sa.Select[tuple[Factura]]:
        return consulta.options(
            selectinload(Factura.proveedor),
            selectinload(Factura.recepciones)
            .selectinload(FacturaRecepcion.recepcion)
            .selectinload(Recepcion.lineas),
            selectinload(Factura.imagenes).selectinload(FacturaImagen.imagen),
        ).execution_options(populate_existing=True)

    def guardar(self, factura: Factura) -> None:
        self._s.add(factura)

    async def por_id(self, negocio_id: uuid.UUID, factura_id: uuid.UUID) -> Factura | None:
        return (
            await self._s.execute(
                self._cargada(
                    sa.select(Factura).where(
                        Factura.negocio_id == negocio_id, Factura.id == factura_id
                    )
                )
            )
        ).scalar_one_or_none()

    async def por_numero(
        self, negocio_id: uuid.UUID, proveedor_id: uuid.UUID, numero: str
    ) -> Factura | None:
        return (
            await self._s.execute(
                sa.select(Factura).where(
                    Factura.negocio_id == negocio_id,
                    Factura.proveedor_id == proveedor_id,
                    Factura.numero == numero,
                )
            )
        ).scalar_one_or_none()

    def _filtrada(
        self,
        negocio_id: uuid.UUID,
        *,
        proveedor_id: uuid.UUID | None,
        estado_pago: str | None,
        desde: date | None,
        hasta: date | None,
    ) -> sa.Select[tuple[Factura]]:
        consulta = sa.select(Factura).where(Factura.negocio_id == negocio_id)
        if proveedor_id is not None:
            consulta = consulta.where(Factura.proveedor_id == proveedor_id)
        if estado_pago is not None:
            consulta = consulta.where(Factura.estado_pago == estado_pago)
        if desde is not None:
            consulta = consulta.where(Factura.fecha_emision >= desde)
        if hasta is not None:
            consulta = consulta.where(Factura.fecha_emision <= hasta)
        return consulta

    async def listar(
        self,
        negocio_id: uuid.UUID,
        *,
        proveedor_id: uuid.UUID | None = None,
        estado_pago: str | None = None,
        desde: date | None = None,
        hasta: date | None = None,
        limite: int,
        despues_de: tuple[date, uuid.UUID] | None,
    ) -> list[Factura]:
        consulta = (
            self._filtrada(
                negocio_id,
                proveedor_id=proveedor_id,
                estado_pago=estado_pago,
                desde=desde,
                hasta=hasta,
            )
            .order_by(Factura.fecha_emision.desc(), Factura.id.desc())
            .limit(limite)
        )
        if despues_de is not None:
            fecha, fid = despues_de
            consulta = consulta.where(
                sa.tuple_(Factura.fecha_emision, Factura.id) < sa.tuple_(fecha, fid)
            )
        return list((await self._s.execute(self._cargada(consulta))).scalars())

    async def total_filtro(
        self,
        negocio_id: uuid.UUID,
        *,
        proveedor_id: uuid.UUID | None = None,
        estado_pago: str | None = None,
        desde: date | None = None,
        hasta: date | None = None,
    ) -> tuple[Decimal, int]:
        """RF-FAC-008: total en moneda base y cantidad de todo el filtro, no de la página."""
        base = self._filtrada(
            negocio_id, proveedor_id=proveedor_id, estado_pago=estado_pago, desde=desde, hasta=hasta
        ).subquery()
        fila = (
            await self._s.execute(
                sa.select(sa.func.coalesce(sa.func.sum(base.c.total_base), 0), sa.func.count())
            )
        ).one()
        return Decimal(fila[0]), int(fila[1])

    async def recepcion_facturada_en(self, recepcion_id: uuid.UUID) -> uuid.UUID | None:
        return (
            await self._s.execute(
                sa.select(FacturaRecepcion.factura_id).where(
                    FacturaRecepcion.recepcion_id == recepcion_id
                )
            )
        ).scalar_one_or_none()
