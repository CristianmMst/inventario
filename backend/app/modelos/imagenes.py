"""imagenes — tabla única para producto y factura (RF-CAT-006, RF-FAC-005, RNF-05, RNF-11)."""

import uuid
from datetime import datetime

import sqlalchemy as sa
from sqlalchemy.orm import Mapped, mapped_column

from app.modelos.base import Base, ConId


class Imagen(ConId, Base):
    """La clave de almacenamiento es opaca y aleatoria; `identificador` es lo único que viaja
    en la URL firmada. Nada se depura automáticamente (RNF-16)."""

    __tablename__ = "imagenes"
    __table_args__ = (
        sa.CheckConstraint("tipo in ('producto', 'factura')", name="tipo_valido"),
        sa.Index("ix_imagenes_negocio_id", "negocio_id"),
    )

    negocio_id: Mapped[uuid.UUID] = mapped_column(
        sa.ForeignKey("negocios.id", ondelete="CASCADE"), nullable=False
    )
    tipo: Mapped[str] = mapped_column(sa.Text, nullable=False)
    clave_almacenamiento: Mapped[str] = mapped_column(sa.String(128), nullable=False, unique=True)
    identificador: Mapped[str] = mapped_column(sa.String(32), nullable=False, unique=True)
    mime: Mapped[str] = mapped_column(sa.Text, nullable=False)
    bytes: Mapped[int] = mapped_column(sa.Integer, nullable=False)
    ancho: Mapped[int] = mapped_column(sa.Integer, nullable=False)
    alto: Mapped[int] = mapped_column(sa.Integer, nullable=False)
    checksum: Mapped[str] = mapped_column(sa.String(64), nullable=False)
    created_at: Mapped[datetime] = mapped_column(
        sa.DateTime(timezone=True), nullable=False, server_default=sa.func.now()
    )
