"""unidades_medida, categorias, productos, codigos_barras — RF-CAT-001..014."""

import uuid
from datetime import datetime
from decimal import Decimal

import sqlalchemy as sa
from sqlalchemy.dialects.postgresql import CITEXT, TSVECTOR
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.modelos.base import Base, ConId, ConMarcasDeTiempo

TIPOS_UNIDAD = ("discreta", "continua")


class UnidadMedida(Base):
    """Catálogo global sembrado por migración; no es por negocio (RF-CAT-004, RN-06)."""

    __tablename__ = "unidades_medida"
    __table_args__ = (
        sa.CheckConstraint("tipo in ('discreta', 'continua')", name="tipo_valido"),
        sa.CheckConstraint("decimales between 0 and 3", name="decimales_rango"),
        sa.CheckConstraint("tipo <> 'discreta' or decimales = 0", name="discreta_sin_decimales"),
    )

    codigo: Mapped[str] = mapped_column(sa.String(16), primary_key=True)
    nombre: Mapped[str] = mapped_column(sa.Text, nullable=False)
    tipo: Mapped[str] = mapped_column(sa.Text, nullable=False)
    decimales: Mapped[int] = mapped_column(sa.SmallInteger, nullable=False)
    orden: Mapped[int] = mapped_column(sa.SmallInteger, nullable=False)


class Categoria(ConId, ConMarcasDeTiempo, Base):
    """Planas, propias del negocio, nombre único sin distinguir mayúsculas (RF-CAT-005)."""

    __tablename__ = "categorias"
    __table_args__ = (sa.UniqueConstraint("negocio_id", "nombre"),)

    negocio_id: Mapped[uuid.UUID] = mapped_column(
        sa.ForeignKey("negocios.id", ondelete="CASCADE"), nullable=False, index=True
    )
    nombre: Mapped[str] = mapped_column(CITEXT, nullable=False)


ESTADOS_PRODUCTO = ("activo", "archivado")
EXPRESION_BUSQUEDA = (
    "to_tsvector('espanol_sin_tildes'::regconfig, coalesce(nombre, '') || ' ' || coalesce(sku, ''))"
)


class Producto(ConId, ConMarcasDeTiempo, Base):
    """RF-CAT-001: una unidad de stock por producto (RN-06); costo y precio vigentes sin
    historial (RF-CAT-013, RN-09); `busqueda` generada con índice GIN (RNF-02)."""

    __tablename__ = "productos"
    __table_args__ = (
        sa.UniqueConstraint("negocio_id", "sku"),
        sa.CheckConstraint("estado in ('activo', 'archivado')", name="estado_valido"),
        sa.CheckConstraint(
            "stock_minimo is null or stock_minimo >= 0", name="stock_minimo_no_negativo"
        ),
        sa.Index(
            "ix_productos_busqueda",
            "busqueda",
            postgresql_using="gin",
            postgresql_with={"fastupdate": "off"},
        ),
        sa.Index("ix_productos_negocio_id_categoria_id", "negocio_id", "categoria_id"),
    )

    negocio_id: Mapped[uuid.UUID] = mapped_column(
        sa.ForeignKey("negocios.id", ondelete="CASCADE"), nullable=False
    )
    sku: Mapped[str] = mapped_column(sa.String(64), nullable=False)
    nombre: Mapped[str] = mapped_column(sa.Text, nullable=False)
    categoria_id: Mapped[uuid.UUID | None] = mapped_column(
        sa.ForeignKey("categorias.id", ondelete="SET NULL")
    )
    unidad_codigo: Mapped[str] = mapped_column(
        sa.ForeignKey("unidades_medida.codigo"), nullable=False
    )
    costo_actual: Mapped[Decimal | None] = mapped_column(sa.Numeric(18, 4))
    precio_venta: Mapped[Decimal | None] = mapped_column(sa.Numeric(18, 4))
    stock_minimo: Mapped[Decimal | None] = mapped_column(sa.Numeric(14, 3))
    imagen_id: Mapped[uuid.UUID | None] = mapped_column(sa.Uuid)
    estado: Mapped[str] = mapped_column(sa.Text, nullable=False, server_default="activo")
    busqueda: Mapped[str] = mapped_column(TSVECTOR, sa.Computed(EXPRESION_BUSQUEDA, persisted=True))

    categoria: Mapped[Categoria | None] = relationship(lazy="raise")
    unidad: Mapped[UnidadMedida] = relationship(lazy="raise")
    codigos_barras: Mapped[list["CodigoBarras"]] = relationship(
        lazy="raise", cascade="all, delete-orphan", order_by="CodigoBarras.codigo"
    )


class CodigoBarras(ConId, Base):
    """RN-05: un código pertenece a un solo producto dentro del negocio; el índice único
    resuelve además el escaneo en una sola lectura (RNF-01)."""

    __tablename__ = "codigos_barras"
    __table_args__ = (sa.UniqueConstraint("negocio_id", "codigo"),)

    negocio_id: Mapped[uuid.UUID] = mapped_column(
        sa.ForeignKey("negocios.id", ondelete="CASCADE"), nullable=False
    )
    producto_id: Mapped[uuid.UUID] = mapped_column(
        sa.ForeignKey("productos.id", ondelete="CASCADE"), nullable=False, index=True
    )
    codigo: Mapped[str] = mapped_column(sa.String(64), nullable=False)
    created_at: Mapped[datetime] = mapped_column(
        sa.DateTime(timezone=True), nullable=False, server_default=sa.func.now()
    )
