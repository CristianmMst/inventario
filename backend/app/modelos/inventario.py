"""movimientos, motivos_movimiento, stock_productos — RF-INV-001..014."""

import uuid
from datetime import datetime
from decimal import Decimal

import sqlalchemy as sa
from sqlalchemy.orm import Mapped, mapped_column

from app.modelos.base import Base

TIPOS_MOVIMIENTO = ("entrada", "salida", "ajuste", "merma", "contramovimiento")
ORIGENES = ("app", "api", "recepcion")
AUTORES = ("usuario", "servicio")


class Movimiento(Base):
    """Hecho de negocio inmutable (RN-02, RF-INV-007): el trigger `movimientos_inmutables`
    rechaza UPDATE y DELETE salvo fijar `anulado_en` una vez. `cantidad` siempre positiva; el
    sentido lo dan `tipo` y `direccion` (RN-07). Autor y momento son el rastro de RNF-13."""

    __tablename__ = "movimientos"
    __table_args__ = (
        sa.CheckConstraint("cantidad > 0", name="cantidad_positiva"),
        sa.CheckConstraint("direccion in (-1, 1)", name="direccion_valida"),
        sa.CheckConstraint(
            "tipo in ('entrada', 'salida', 'ajuste', 'merma', 'contramovimiento')",
            name="tipo_valido",
        ),
        sa.CheckConstraint("origen in ('app', 'api', 'recepcion')", name="origen_valido"),
        sa.CheckConstraint("autor_tipo in ('usuario', 'servicio')", name="autor_tipo_valido"),
        # La pareja (tipo, motivo) debe existir en la semilla: la base impide un motivo ajeno.
        sa.ForeignKeyConstraint(
            ["tipo", "motivo"],
            ["motivos_movimiento.tipo_movimiento", "motivos_movimiento.codigo"],
            name="fk_movimientos_tipo_motivo_motivos_movimiento",
            ondelete="RESTRICT",
        ),
        sa.Index(
            "ix_movimientos_producto_ocurrido",
            "producto_id",
            sa.text("ocurrido_en DESC"),
            sa.text("id DESC"),
        ),
        sa.Index("ix_movimientos_negocio_ocurrido", "negocio_id", "ocurrido_en"),
    )

    id: Mapped[uuid.UUID] = mapped_column(
        sa.Uuid, primary_key=True, server_default=sa.text("gen_random_uuid()")
    )
    negocio_id: Mapped[uuid.UUID] = mapped_column(
        sa.ForeignKey("negocios.id", ondelete="RESTRICT"), nullable=False
    )
    producto_id: Mapped[uuid.UUID] = mapped_column(
        sa.ForeignKey("productos.id", ondelete="RESTRICT"), nullable=False
    )
    tipo: Mapped[str] = mapped_column(sa.Text, nullable=False)
    cantidad: Mapped[Decimal] = mapped_column(sa.Numeric(14, 3), nullable=False)
    direccion: Mapped[int] = mapped_column(sa.SmallInteger, nullable=False)
    motivo: Mapped[str] = mapped_column(sa.String(32), nullable=False)
    nota: Mapped[str | None] = mapped_column(sa.Text)
    forzado: Mapped[bool] = mapped_column(sa.Boolean, nullable=False, server_default=sa.false())
    stock_resultante: Mapped[Decimal] = mapped_column(sa.Numeric(14, 3), nullable=False)
    anulado_en: Mapped[datetime | None] = mapped_column(sa.DateTime(timezone=True))
    anula_movimiento_id: Mapped[uuid.UUID | None] = mapped_column(
        sa.ForeignKey("movimientos.id", ondelete="RESTRICT"), index=True
    )
    recepcion_id: Mapped[uuid.UUID | None] = mapped_column(
        sa.ForeignKey("recepciones.id", ondelete="RESTRICT"), index=True
    )
    recepcion_linea_id: Mapped[uuid.UUID | None] = mapped_column(
        sa.ForeignKey("recepciones_lineas.id", ondelete="RESTRICT"), index=True
    )
    origen: Mapped[str] = mapped_column(sa.Text, nullable=False)
    autor_tipo: Mapped[str] = mapped_column(sa.Text, nullable=False)
    autor_id: Mapped[uuid.UUID] = mapped_column(sa.Uuid, nullable=False)
    ocurrido_en: Mapped[datetime] = mapped_column(
        sa.DateTime(timezone=True), nullable=False, server_default=sa.func.now()
    )
    created_at: Mapped[datetime] = mapped_column(
        sa.DateTime(timezone=True), nullable=False, server_default=sa.func.now()
    )


class MotivoMovimiento(Base):
    """RF-INV-010: lista cerrada por tipo, semilla global, sin administración por el usuario.
    Cada tipo lleva `otro` con `exige_nota`. Ampliarla es una migración de datos."""

    __tablename__ = "motivos_movimiento"
    __table_args__ = (
        sa.CheckConstraint(
            "tipo_movimiento in ('entrada', 'salida', 'ajuste', 'merma', 'contramovimiento')",
            name="tipo_valido",
        ),
    )

    tipo_movimiento: Mapped[str] = mapped_column(sa.Text, primary_key=True)
    codigo: Mapped[str] = mapped_column(sa.String(32), primary_key=True)
    etiqueta: Mapped[str] = mapped_column(sa.Text, nullable=False)
    exige_nota: Mapped[bool] = mapped_column(sa.Boolean, nullable=False)
    orden: Mapped[int] = mapped_column(sa.SmallInteger, nullable=False)


class StockProducto(Base):
    """Instantánea materializada del stock (RNF-01, RNF-03). No es fuente de verdad: la
    verdad es la suma de movimientos (RN-01), y la reconciliación lo comprueba. `bajo_minimo`
    guarda el estado anterior para el antirrebote de RN-22."""

    __tablename__ = "stock_productos"
    __table_args__ = (
        sa.Index(
            "ix_stock_bajo_minimo",
            "negocio_id",
            postgresql_where=sa.text("bajo_minimo"),
        ),
    )

    producto_id: Mapped[uuid.UUID] = mapped_column(
        sa.ForeignKey("productos.id", ondelete="CASCADE"), primary_key=True
    )
    negocio_id: Mapped[uuid.UUID] = mapped_column(
        sa.ForeignKey("negocios.id", ondelete="CASCADE"), nullable=False
    )
    cantidad: Mapped[Decimal] = mapped_column(sa.Numeric(14, 3), nullable=False, server_default="0")
    bajo_minimo: Mapped[bool] = mapped_column(sa.Boolean, nullable=False, server_default=sa.false())
    actualizado_en: Mapped[datetime] = mapped_column(
        sa.DateTime(timezone=True), nullable=False, server_default=sa.func.now()
    )
