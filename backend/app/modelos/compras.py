"""proveedores, ordenes_compra, recepciones — RF-COM-001..013."""

import uuid

import sqlalchemy as sa
from sqlalchemy.orm import Mapped, mapped_column

from app.modelos.base import Base, ConId, ConMarcasDeTiempo

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
