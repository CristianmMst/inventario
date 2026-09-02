"""proveedores, ordenes_compra, recepciones — RF-COM-001..013."""

import uuid
from datetime import date, datetime
from decimal import Decimal

import sqlalchemy as sa
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.modelos.base import Base, ConId, ConMarcasDeTiempo
from app.modelos.catalogo import Producto

ESTADOS_PROVEEDOR = ("activo", "archivado")


class Proveedor(ConId, ConMarcasDeTiempo, Base):
    """RF-COM-001. Con documentos asociados no se borra: se archiva (RN-17)."""

    __tablename__ = "proveedores"
    __table_args__ = (
        sa.CheckConstraint("estado in ('activo', 'archivado')", name="estado_valido"),
        sa.Index("ix_proveedores_negocio_id_nombre", "negocio_id", "nombre"),
    )

    negocio_id: Mapped[uuid.UUID] = mapped_column(
        sa.ForeignKey("negocios.id", ondelete="CASCADE"), nullable=False
    )
    nombre: Mapped[str] = mapped_column(sa.Text, nullable=False)
    identificacion_fiscal: Mapped[str | None] = mapped_column(sa.Text)
    contacto: Mapped[str | None] = mapped_column(sa.Text)
    telefono: Mapped[str | None] = mapped_column(sa.Text)
    email: Mapped[str | None] = mapped_column(sa.Text)
    direccion: Mapped[str | None] = mapped_column(sa.Text)
    notas: Mapped[str | None] = mapped_column(sa.Text)
    estado: Mapped[str] = mapped_column(sa.Text, nullable=False, server_default="activo")


ESTADOS_ORDEN = (
    "borrador",
    "emitida",
    "parcialmente_recibida",
    "recibida",
    "cerrada_con_faltante",
    "cancelada",
)


class OrdenCompra(ConId, ConMarcasDeTiempo, Base):
    """RF-COM-002 / RF-COM-003. Herramienta de planificación opcional (RN-11)."""

    __tablename__ = "ordenes_compra"
    __table_args__ = (
        sa.UniqueConstraint("negocio_id", "secuencia"),
        sa.CheckConstraint(
            "estado in ('borrador', 'emitida', 'parcialmente_recibida', 'recibida',"
            " 'cerrada_con_faltante', 'cancelada')",
            name="estado_valido",
        ),
        sa.Index("ix_ordenes_compra_negocio_id_proveedor_id", "negocio_id", "proveedor_id"),
    )

    negocio_id: Mapped[uuid.UUID] = mapped_column(
        sa.ForeignKey("negocios.id", ondelete="CASCADE"), nullable=False
    )
    proveedor_id: Mapped[uuid.UUID] = mapped_column(
        sa.ForeignKey("proveedores.id", ondelete="RESTRICT"), nullable=False
    )
    secuencia: Mapped[int] = mapped_column(sa.Integer, nullable=False)
    estado: Mapped[str] = mapped_column(sa.Text, nullable=False, server_default="borrador")
    fecha_esperada: Mapped[date | None] = mapped_column(sa.Date)
    moneda: Mapped[str] = mapped_column(sa.CHAR(3), nullable=False)
    notas: Mapped[str | None] = mapped_column(sa.Text)
    motivo_cierre: Mapped[str | None] = mapped_column(sa.Text)
    emitida_en: Mapped[datetime | None] = mapped_column(sa.DateTime(timezone=True))
    cerrada_en: Mapped[datetime | None] = mapped_column(sa.DateTime(timezone=True))

    lineas: Mapped[list["OrdenCompraLinea"]] = relationship(
        lazy="raise", cascade="all, delete-orphan", order_by="OrdenCompraLinea.posicion"
    )
    proveedor: Mapped[Proveedor] = relationship(lazy="raise")

    @property
    def numero(self) -> str:
        return f"OC-{self.secuencia:06d}"


class OrdenCompraLinea(ConId, Base):
    """La cantidad pendiente se calcula (ordenada - Σ recibida), no se guarda (plan.md §3.2)."""

    __tablename__ = "ordenes_compra_lineas"
    __table_args__ = (
        sa.UniqueConstraint("orden_id", "producto_id"),
        sa.CheckConstraint("cantidad_ordenada > 0", name="cantidad_positiva"),
    )

    orden_id: Mapped[uuid.UUID] = mapped_column(
        sa.ForeignKey("ordenes_compra.id", ondelete="CASCADE"), nullable=False
    )
    producto_id: Mapped[uuid.UUID] = mapped_column(
        sa.ForeignKey("productos.id", ondelete="RESTRICT"), nullable=False
    )
    posicion: Mapped[int] = mapped_column(sa.SmallInteger, nullable=False)
    cantidad_ordenada: Mapped[Decimal] = mapped_column(sa.Numeric(14, 3), nullable=False)
    costo_unitario_estimado: Mapped[Decimal | None] = mapped_column(sa.Numeric(18, 4))

    producto: Mapped[Producto] = relationship(lazy="raise")


ESTADOS_RECEPCION = ("borrador", "confirmada", "corregida")


class Recepcion(ConId, ConMarcasDeTiempo, Base):
    """RF-COM-004/005: con o sin orden; `orden_id` nulo es el caso normal, no uno especial.
    Confirmada es inmutable (RF-COM-012); anular sus movimientos la marca corregida."""

    __tablename__ = "recepciones"
    __table_args__ = (
        sa.UniqueConstraint("negocio_id", "secuencia"),
        sa.CheckConstraint(
            "estado in ('borrador', 'confirmada', 'corregida')", name="estado_valido"
        ),
        sa.CheckConstraint("tasa_cambio > 0", name="tasa_positiva"),
        sa.Index("ix_recepciones_negocio_id_fecha", "negocio_id", "fecha"),
        sa.Index("ix_recepciones_orden_id", "orden_id"),
    )

    negocio_id: Mapped[uuid.UUID] = mapped_column(
        sa.ForeignKey("negocios.id", ondelete="CASCADE"), nullable=False
    )
    proveedor_id: Mapped[uuid.UUID] = mapped_column(
        sa.ForeignKey("proveedores.id", ondelete="RESTRICT"), nullable=False, index=True
    )
    orden_id: Mapped[uuid.UUID | None] = mapped_column(
        sa.ForeignKey("ordenes_compra.id", ondelete="RESTRICT")
    )
    secuencia: Mapped[int] = mapped_column(sa.Integer, nullable=False)
    fecha: Mapped[date] = mapped_column(
        sa.Date, nullable=False, server_default=sa.func.current_date()
    )
    moneda: Mapped[str] = mapped_column(sa.CHAR(3), nullable=False)
    tasa_cambio: Mapped[Decimal] = mapped_column(
        sa.Numeric(18, 8), nullable=False, server_default="1"
    )
    estado: Mapped[str] = mapped_column(sa.Text, nullable=False, server_default="borrador")
    notas: Mapped[str | None] = mapped_column(sa.Text)
    confirmada_en: Mapped[datetime | None] = mapped_column(sa.DateTime(timezone=True))

    lineas: Mapped[list["RecepcionLinea"]] = relationship(
        lazy="raise", cascade="all, delete-orphan", order_by="RecepcionLinea.posicion"
    )
    proveedor: Mapped[Proveedor] = relationship(lazy="raise")
    orden: Mapped[OrdenCompra | None] = relationship(lazy="raise")

    @property
    def numero(self) -> str:
        return f"RC-{self.secuencia:06d}"


class RecepcionLinea(ConId, Base):
    """RN-08: costo unitario, moneda, tasa y equivalente base quedan congelados al confirmar."""

    __tablename__ = "recepciones_lineas"
    __table_args__ = (
        sa.UniqueConstraint("recepcion_id", "producto_id"),
        sa.CheckConstraint("cantidad_recibida > 0", name="cantidad_positiva"),
        sa.CheckConstraint("costo_unitario >= 0", name="costo_no_negativo"),
    )

    recepcion_id: Mapped[uuid.UUID] = mapped_column(
        sa.ForeignKey("recepciones.id", ondelete="CASCADE"), nullable=False
    )
    orden_linea_id: Mapped[uuid.UUID | None] = mapped_column(
        sa.ForeignKey("ordenes_compra_lineas.id", ondelete="RESTRICT")
    )
    producto_id: Mapped[uuid.UUID] = mapped_column(
        sa.ForeignKey("productos.id", ondelete="RESTRICT"), nullable=False
    )
    posicion: Mapped[int] = mapped_column(sa.SmallInteger, nullable=False)
    cantidad_recibida: Mapped[Decimal] = mapped_column(sa.Numeric(14, 3), nullable=False)
    costo_unitario: Mapped[Decimal] = mapped_column(sa.Numeric(18, 4), nullable=False)
    moneda_costo: Mapped[str] = mapped_column(sa.CHAR(3), nullable=False)
    tasa_cambio: Mapped[Decimal | None] = mapped_column(sa.Numeric(18, 8))
    costo_unitario_base: Mapped[Decimal | None] = mapped_column(sa.Numeric(18, 4))
    exceso: Mapped[bool] = mapped_column(sa.Boolean, nullable=False, server_default=sa.false())

    producto: Mapped[Producto] = relationship(lazy="raise")
