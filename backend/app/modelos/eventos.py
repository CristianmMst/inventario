"""eventos y suscripciones_webhook — RF-INT-001..007."""

import uuid
from datetime import datetime
from typing import Any

import sqlalchemy as sa
from sqlalchemy.dialects.postgresql import ARRAY, JSONB
from sqlalchemy.orm import Mapped, mapped_column

from app.modelos.base import Base, ConId, ConMarcasDeTiempo


class Evento(Base):
    """Bandeja de salida transaccional en todo menos el nombre (plan.md §7). `secuencia` es
    BIGSERIAL, no un timestamp: es el cursor con el que un consumidor se pone al día."""

    __tablename__ = "eventos"
    __table_args__ = (
        sa.Index("ix_eventos_negocio_secuencia", "negocio_id", "secuencia"),
        sa.Index("ix_eventos_negocio_tipo_secuencia", "negocio_id", "tipo", "secuencia"),
    )

    id: Mapped[uuid.UUID] = mapped_column(sa.Uuid, primary_key=True)
    secuencia: Mapped[int] = mapped_column(
        sa.BigInteger, sa.Identity(always=True), nullable=False, unique=True
    )
    negocio_id: Mapped[uuid.UUID] = mapped_column(
        sa.ForeignKey("negocios.id", ondelete="RESTRICT"), nullable=False
    )
    tipo: Mapped[str] = mapped_column(sa.Text, nullable=False)
    version: Mapped[int] = mapped_column(sa.SmallInteger, nullable=False)
    ocurrido_en: Mapped[datetime] = mapped_column(sa.DateTime(timezone=True), nullable=False)
    autor_tipo: Mapped[str] = mapped_column(sa.Text, nullable=False)
    autor_id: Mapped[uuid.UUID] = mapped_column(sa.Uuid, nullable=False)
    autor_nombre: Mapped[str] = mapped_column(sa.Text, nullable=False)
    payload: Mapped[dict[str, Any]] = mapped_column(JSONB, nullable=False)
    created_at: Mapped[datetime] = mapped_column(
        sa.DateTime(timezone=True), nullable=False, server_default=sa.func.now()
    )


class SuscripcionWebhook(ConId, ConMarcasDeTiempo, Base):
    """RF-INT-005: contrato de webhooks salientes. Se persiste; en v1 no se entrega."""

    __tablename__ = "suscripciones_webhook"
    __table_args__ = (sa.Index("ix_suscripciones_webhook_negocio_id", "negocio_id"),)

    negocio_id: Mapped[uuid.UUID] = mapped_column(
        sa.ForeignKey("negocios.id", ondelete="CASCADE"), nullable=False
    )
    url: Mapped[str] = mapped_column(sa.Text, nullable=False)
    tipos: Mapped[list[str]] = mapped_column(ARRAY(sa.Text), nullable=False)
    secreto_hash: Mapped[str] = mapped_column(sa.Text, nullable=False)
    activa: Mapped[bool] = mapped_column(sa.Boolean, nullable=False, server_default=sa.true())
    descripcion: Mapped[str | None] = mapped_column(sa.Text)
