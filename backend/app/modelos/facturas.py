"""facturas, facturas_recepciones, facturas_imagenes — RF-FAC-001..008, RN-18."""

import uuid
from datetime import date, datetime
from decimal import Decimal

import sqlalchemy as sa
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.modelos.base import Base, ConId, ConMarcasDeTiempo
from app.modelos.compras import Proveedor, Recepcion
from app.modelos.imagenes import Imagen

ESTADOS_PAGO = ("pendiente", "pagada", "anulada")


class Factura(ConId, ConMarcasDeTiempo, Base):
    """Factura de compra (RF-FAC-001). `ck_facturas_cuadre` hace que la base, no solo Pydantic,
    exija base + impuesto = total (RN-18); el número es único por proveedor (RF-FAC-002)."""

    __tablename__ = "facturas"
    __table_args__ = (
        sa.UniqueConstraint("negocio_id", "proveedor_id", "numero"),
        sa.CheckConstraint("base_gravable + impuesto = total", name="cuadre"),
        sa.CheckConstraint(
            "estado_pago in ('pendiente', 'pagada', 'anulada')", name="estado_pago_valido"
        ),
        sa.CheckConstraint("tasa_cambio > 0", name="tasa_positiva"),
        sa.Index("ix_facturas_negocio_id_fecha_emision", "negocio_id", "fecha_emision"),
        sa.Index("ix_facturas_negocio_id_estado_pago", "negocio_id", "estado_pago"),
    )

    negocio_id: Mapped[uuid.UUID] = mapped_column(
        sa.ForeignKey("negocios.id", ondelete="CASCADE"), nullable=False
    )
    proveedor_id: Mapped[uuid.UUID] = mapped_column(
        sa.ForeignKey("proveedores.id", ondelete="RESTRICT"), nullable=False
    )
    numero: Mapped[str] = mapped_column(sa.String(64), nullable=False)
    fecha_emision: Mapped[date] = mapped_column(sa.Date, nullable=False)
    fecha_vencimiento: Mapped[date | None] = mapped_column(sa.Date)
    moneda: Mapped[str] = mapped_column(sa.CHAR(3), nullable=False)
    base_gravable: Mapped[Decimal] = mapped_column(sa.Numeric(18, 4), nullable=False)
    impuesto: Mapped[Decimal] = mapped_column(sa.Numeric(18, 4), nullable=False)
    total: Mapped[Decimal] = mapped_column(sa.Numeric(18, 4), nullable=False)
    tasa_cambio: Mapped[Decimal] = mapped_column(
        sa.Numeric(18, 8), nullable=False, server_default="1"
    )
    total_base: Mapped[Decimal] = mapped_column(sa.Numeric(18, 4), nullable=False)
    estado_pago: Mapped[str] = mapped_column(sa.Text, nullable=False, server_default="pendiente")
    fecha_pago: Mapped[date | None] = mapped_column(sa.Date)
    motivo_anulacion: Mapped[str | None] = mapped_column(sa.Text)
    notas: Mapped[str | None] = mapped_column(sa.Text)

    proveedor: Mapped[Proveedor] = relationship(lazy="raise")
    recepciones: Mapped[list["FacturaRecepcion"]] = relationship(
        lazy="raise", cascade="all, delete-orphan"
    )
    imagenes: Mapped[list["FacturaImagen"]] = relationship(
        lazy="raise", cascade="all, delete-orphan", order_by="FacturaImagen.orden"
    )


class FacturaRecepcion(Base):
    """RF-FAC-006: una recepción aparece en una sola factura (uq sobre recepcion_id)."""

    __tablename__ = "facturas_recepciones"

    factura_id: Mapped[uuid.UUID] = mapped_column(
        sa.ForeignKey("facturas.id", ondelete="CASCADE"), primary_key=True
    )
    recepcion_id: Mapped[uuid.UUID] = mapped_column(
        sa.ForeignKey("recepciones.id", ondelete="RESTRICT"), primary_key=True, unique=True
    )

    recepcion: Mapped[Recepcion] = relationship(lazy="raise")


class FacturaImagen(Base):
    """RF-FAC-005: una o varias imágenes por factura, ordenadas."""

    __tablename__ = "facturas_imagenes"
    __table_args__ = (sa.UniqueConstraint("factura_id", "orden"),)

    factura_id: Mapped[uuid.UUID] = mapped_column(
        sa.ForeignKey("facturas.id", ondelete="CASCADE"), primary_key=True
    )
    imagen_id: Mapped[uuid.UUID] = mapped_column(
        sa.ForeignKey("imagenes.id", ondelete="RESTRICT"), primary_key=True, unique=True
    )
    orden: Mapped[int] = mapped_column(sa.SmallInteger, nullable=False)
    created_at: Mapped[datetime] = mapped_column(
        sa.DateTime(timezone=True), nullable=False, server_default=sa.func.now()
    )

    imagen: Mapped[Imagen] = relationship(lazy="raise")
