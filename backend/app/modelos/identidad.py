"""usuarios, negocios, membresias, refresh_tokens — RF-AUT-001..004, RN-19."""

import uuid
from datetime import datetime

import sqlalchemy as sa
from sqlalchemy.dialects.postgresql import CITEXT
from sqlalchemy.orm import Mapped, mapped_column

from app.modelos.base import Base, ConId, ConMarcasDeTiempo

ROLES_MEMBRESIA = ("dueno",)
rol_membresia = sa.Enum(*ROLES_MEMBRESIA, name="rol_membresia", create_type=False)


class Usuario(ConId, ConMarcasDeTiempo, Base):
    __tablename__ = "usuarios"

    email: Mapped[str] = mapped_column(CITEXT, nullable=False, unique=True)
    password_hash: Mapped[str] = mapped_column(sa.Text, nullable=False)
    nombre: Mapped[str] = mapped_column(sa.Text, nullable=False)


class Negocio(ConId, ConMarcasDeTiempo, Base):
    __tablename__ = "negocios"

    nombre: Mapped[str] = mapped_column(sa.Text, nullable=False)
    moneda_base: Mapped[str] = mapped_column(sa.CHAR(3), nullable=False)
    zona_horaria: Mapped[str] = mapped_column(sa.Text, nullable=False)


class Membresia(ConId, ConMarcasDeTiempo, Base):
    """Tabla puente desde el día uno. v1 crea una sola fila con rol `dueno`."""

    __tablename__ = "membresias"
    __table_args__ = (sa.UniqueConstraint("usuario_id", "negocio_id"),)

    usuario_id: Mapped[uuid.UUID] = mapped_column(
        sa.ForeignKey("usuarios.id", ondelete="CASCADE"), nullable=False
    )
    negocio_id: Mapped[uuid.UUID] = mapped_column(
        sa.ForeignKey("negocios.id", ondelete="CASCADE"), nullable=False, index=True
    )
    rol: Mapped[str] = mapped_column(rol_membresia, nullable=False)


class RefreshToken(ConId, Base):
    """Token de renovación opaco: solo se guarda su hash. Rotación por familia (plan.md §5)."""

    __tablename__ = "refresh_tokens"

    usuario_id: Mapped[uuid.UUID] = mapped_column(
        sa.ForeignKey("usuarios.id", ondelete="CASCADE"), nullable=False, index=True
    )
    token_hash: Mapped[str] = mapped_column(sa.Text, nullable=False, unique=True)
    familia_id: Mapped[uuid.UUID] = mapped_column(sa.Uuid, nullable=False, index=True)
    expira_en: Mapped[datetime] = mapped_column(sa.DateTime(timezone=True), nullable=False)
    revocado_en: Mapped[datetime | None] = mapped_column(sa.DateTime(timezone=True))
    created_at: Mapped[datetime] = mapped_column(
        sa.DateTime(timezone=True), nullable=False, server_default=sa.func.now()
    )
