"""Consultas de los siete reportes (RF-REP-001..007). Todas filtran por negocio (RN-19) y
leen la instantánea `stock_productos`, nunca recalculan sumando movimientos (RNF-04)."""

import uuid
from datetime import date, datetime, timedelta
from decimal import Decimal
from typing import Any

import sqlalchemy as sa
from sqlalchemy.ext.asyncio import AsyncSession

from app.modelos.catalogo import Categoria, Producto
from app.modelos.compras import Proveedor, Recepcion, RecepcionLinea
from app.modelos.facturas import Factura
from app.modelos.inventario import MotivoMovimiento, Movimiento, StockProducto

STOCK = sa.func.coalesce(StockProducto.cantidad, 0)


def _producto_cols() -> list[Any]:
    return [
        Producto.id.label("producto_id"),
        Producto.nombre.label("producto_nombre"),
        Producto.sku.label("producto_sku"),
        Producto.unidad_codigo.label("producto_unidad"),
    ]


class RepositorioReportes:
    def __init__(self, sesion: AsyncSession) -> None:
        self._s = sesion

    def _activos(self, negocio_id: uuid.UUID) -> sa.Select[Any]:
        return (
            sa.select(
                *_producto_cols(),
                STOCK.label("stock"),
                Producto.stock_minimo,
                Producto.costo_actual,
            )
            .select_from(Producto)
            .outerjoin(StockProducto, StockProducto.producto_id == Producto.id)
            .where(Producto.negocio_id == negocio_id, Producto.estado == "activo")
        )

    async def bajo_minimo(
        self, negocio_id: uuid.UUID, limite: int, despues_de: tuple[Decimal, uuid.UUID] | None
    ) -> list[sa.Row[Any]]:
        """RF-REP-001: stock ≤ mínimo, con mínimo definido, por déficit relativo descendente.
        Lee solo lo alertado gracias al índice parcial de `bajo_minimo`."""
        deficit = (Producto.stock_minimo - STOCK).label("deficit")
        relativo = sa.case(
            (Producto.stock_minimo > 0, (Producto.stock_minimo - STOCK) / Producto.stock_minimo),
            else_=sa.literal(Decimal(1)),
        ).label("deficit_relativo")
        consulta = (
            sa.select(
                *_producto_cols(), STOCK.label("stock"), Producto.stock_minimo, deficit, relativo
            )
            .select_from(Producto)
            .join(StockProducto, StockProducto.producto_id == Producto.id)
            .where(
                Producto.negocio_id == negocio_id,
                Producto.estado == "activo",
                Producto.stock_minimo.is_not(None),
                StockProducto.bajo_minimo.is_(True),
            )
            .order_by(sa.desc("deficit_relativo"), Producto.id)
            .limit(limite)
        )
        if despues_de is not None:
            r, pid = despues_de
            consulta = consulta.where(
                sa.tuple_(relativo, Producto.id) < sa.tuple_(sa.literal(r), sa.literal(pid))
            )
        return list((await self._s.execute(consulta)).all())

    async def agotados(
        self, negocio_id: uuid.UUID, limite: int, despues_de: tuple[str, uuid.UUID] | None
    ) -> list[sa.Row[Any]]:
        """RF-REP-007: activos con stock ≤ 0, tengan o no mínimo."""
        consulta = (
            self._activos(negocio_id)
            .where(STOCK <= 0)
            .order_by(Producto.nombre, Producto.id)
            .limit(limite)
        )
        if despues_de is not None:
            consulta = consulta.where(
                sa.tuple_(Producto.nombre, Producto.id) > sa.tuple_(*despues_de)
            )
        return list((await self._s.execute(consulta)).all())

    async def sin_movimiento(
        self,
        negocio_id: uuid.UUID,
        dias: int,
        limite: int,
        despues_de: tuple[str, uuid.UUID] | None,
    ) -> list[sa.Row[Any]]:
        """RF-REP-002: activos sin movimientos en N días, excluyendo los creados en el período."""
        umbral = sa.func.now() - timedelta(days=dias)
        ultimo = (
            sa.select(sa.func.max(Movimiento.ocurrido_en))
            .where(Movimiento.producto_id == Producto.id)
            .correlate(Producto)
            .scalar_subquery()
        )
        consulta = (
            self._activos(negocio_id)
            .add_columns(ultimo.label("ultimo_movimiento_en"), Producto.created_at)
            .where(Producto.created_at < umbral, sa.or_(ultimo.is_(None), ultimo < umbral))
            .order_by(Producto.nombre, Producto.id)
            .limit(limite)
        )
        if despues_de is not None:
            consulta = consulta.where(
                sa.tuple_(Producto.nombre, Producto.id) > sa.tuple_(*despues_de)
            )
        return list((await self._s.execute(consulta)).all())

    async def valorizacion(self, negocio_id: uuid.UUID) -> tuple[Decimal, int, list[sa.Row[Any]]]:
        """RF-REP-003: Σ stock × costo actual de lo que tiene stock y costo; desglose por
        categoría."""
        valor = sa.func.sum(STOCK * Producto.costo_actual)
        base = (
            sa.select(
                Categoria.id.label("categoria_id"),
                Categoria.nombre.label("categoria_nombre"),
                sa.func.count().label("productos"),
                valor.label("valor"),
            )
            .select_from(Producto)
            .outerjoin(StockProducto, StockProducto.producto_id == Producto.id)
            .outerjoin(Categoria, Categoria.id == Producto.categoria_id)
            .where(
                Producto.negocio_id == negocio_id,
                Producto.estado == "activo",
                Producto.costo_actual.is_not(None),
                STOCK > 0,
            )
            .group_by(Categoria.id, Categoria.nombre)
            .order_by(sa.nulls_last(Categoria.nombre))
        )
        filas = list((await self._s.execute(base)).all())
        total = sum((Decimal(f.valor) for f in filas), Decimal(0))
        productos = sum(int(f.productos) for f in filas)
        return total, productos, filas

    async def no_valorizables(
        self, negocio_id: uuid.UUID, limite: int, despues_de: tuple[str, uuid.UUID] | None
    ) -> list[sa.Row[Any]]:
        consulta = (
            self._activos(negocio_id)
            .where(Producto.costo_actual.is_(None), STOCK > 0)
            .order_by(Producto.nombre, Producto.id)
            .limit(limite)
        )
        if despues_de is not None:
            consulta = consulta.where(
                sa.tuple_(Producto.nombre, Producto.id) > sa.tuple_(*despues_de)
            )
        return list((await self._s.execute(consulta)).all())

    async def compras(self, negocio_id: uuid.UUID, desde: date, hasta: date) -> dict[str, Any]:
        """RF-REP-005: recibido (líneas × costo base congelado) y facturado (total_base) por
        proveedor y por categoría, en moneda base."""
        recibido_linea = RecepcionLinea.cantidad_recibida * sa.func.coalesce(
            RecepcionLinea.costo_unitario_base, 0
        )
        filtro_rec = sa.and_(
            Recepcion.negocio_id == negocio_id,
            Recepcion.estado != "borrador",
            Recepcion.fecha >= desde,
            Recepcion.fecha <= hasta,
        )
        por_prov_rec = (
            sa.select(
                Recepcion.proveedor_id,
                sa.func.sum(recibido_linea).label("recibido"),
                sa.func.count(sa.distinct(Recepcion.id)).label("recepciones"),
            )
            .select_from(Recepcion)
            .join(RecepcionLinea, RecepcionLinea.recepcion_id == Recepcion.id)
            .where(filtro_rec)
            .group_by(Recepcion.proveedor_id)
        )
        filtro_fac = sa.and_(
            Factura.negocio_id == negocio_id,
            Factura.estado_pago != "anulada",
            Factura.fecha_emision >= desde,
            Factura.fecha_emision <= hasta,
        )
        por_prov_fac = (
            sa.select(
                Factura.proveedor_id,
                sa.func.sum(Factura.total_base).label("facturado"),
                sa.func.count().label("facturas"),
            )
            .where(filtro_fac)
            .group_by(Factura.proveedor_id)
        )
        por_cat = (
            sa.select(
                Categoria.id.label("categoria_id"),
                Categoria.nombre.label("categoria_nombre"),
                sa.func.sum(recibido_linea).label("recibido"),
            )
            .select_from(Recepcion)
            .join(RecepcionLinea, RecepcionLinea.recepcion_id == Recepcion.id)
            .join(Producto, Producto.id == RecepcionLinea.producto_id)
            .outerjoin(Categoria, Categoria.id == Producto.categoria_id)
            .where(filtro_rec)
            .group_by(Categoria.id, Categoria.nombre)
            .order_by(sa.nulls_last(Categoria.nombre))
        )
        recibidos = {f.proveedor_id: f for f in (await self._s.execute(por_prov_rec)).all()}
        facturados = {f.proveedor_id: f for f in (await self._s.execute(por_prov_fac)).all()}
        ids = set(recibidos) | set(facturados)
        proveedores = {}
        if ids:
            filas = (
                await self._s.execute(
                    sa.select(Proveedor.id, Proveedor.nombre).where(Proveedor.id.in_(ids))
                )
            ).all()
            proveedores = {f.id: f.nombre for f in filas}
        return {
            "recibidos": recibidos,
            "facturados": facturados,
            "proveedores": proveedores,
            "por_categoria": list((await self._s.execute(por_cat)).all()),
        }

    async def mermas(self, negocio_id: uuid.UUID, desde: date, hasta: date) -> dict[str, Any]:
        """RF-REP-006 / RN-16: solo tipo merma y no anuladas, valorizadas al costo actual."""
        valor = Movimiento.cantidad * sa.func.coalesce(Producto.costo_actual, 0)
        filtro = sa.and_(
            Movimiento.negocio_id == negocio_id,
            Movimiento.tipo == "merma",
            Movimiento.anulado_en.is_(None),
            sa.cast(Movimiento.ocurrido_en, sa.Date) >= desde,
            sa.cast(Movimiento.ocurrido_en, sa.Date) <= hasta,
        )
        por_motivo = (
            sa.select(
                Movimiento.motivo,
                MotivoMovimiento.etiqueta,
                sa.func.sum(Movimiento.cantidad).label("cantidad"),
                sa.func.sum(valor).label("valor"),
            )
            .select_from(Movimiento)
            .join(Producto, Producto.id == Movimiento.producto_id)
            .join(
                MotivoMovimiento,
                sa.and_(
                    MotivoMovimiento.codigo == Movimiento.motivo,
                    MotivoMovimiento.tipo_movimiento == Movimiento.tipo,
                ),
            )
            .where(filtro)
            .group_by(Movimiento.motivo, MotivoMovimiento.etiqueta)
            .order_by(sa.desc("valor"), Movimiento.motivo)
        )
        por_producto = (
            sa.select(
                *_producto_cols(),
                sa.func.sum(Movimiento.cantidad).label("cantidad"),
                sa.func.sum(valor).label("valor"),
            )
            .select_from(Movimiento)
            .join(Producto, Producto.id == Movimiento.producto_id)
            .where(filtro)
            .group_by(Producto.id, Producto.nombre, Producto.sku, Producto.unidad_codigo)
            .order_by(sa.desc("valor"), Producto.nombre, Producto.id)
        )
        return {
            "por_motivo": list((await self._s.execute(por_motivo)).all()),
            "por_producto": list((await self._s.execute(por_producto)).all()),
        }

    async def discrepancias(
        self,
        negocio_id: uuid.UUID,
        desde: date,
        hasta: date,
        limite: int,
        despues_de: tuple[datetime, uuid.UUID] | None,
    ) -> list[sa.Row[Any]]:
        """RN-04: movimientos forzados del período, del más reciente al más antiguo."""
        consulta = (
            sa.select(Movimiento, *_producto_cols())
            .join(Producto, Producto.id == Movimiento.producto_id)
            .where(
                Movimiento.negocio_id == negocio_id,
                Movimiento.forzado.is_(True),
                sa.cast(Movimiento.ocurrido_en, sa.Date) >= desde,
                sa.cast(Movimiento.ocurrido_en, sa.Date) <= hasta,
            )
            .order_by(Movimiento.ocurrido_en.desc(), Movimiento.id.desc())
            .limit(limite)
        )
        if despues_de is not None:
            consulta = consulta.where(
                sa.tuple_(Movimiento.ocurrido_en, Movimiento.id) < sa.tuple_(*despues_de)
            )
        return list((await self._s.execute(consulta)).all())
