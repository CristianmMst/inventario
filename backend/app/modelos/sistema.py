"""Tablas de infraestructura de la API: operaciones_idempotentes (RN-20, RNF-06)."""

import uuid
from typing import Any

import sqlalchemy as sa
from sqlalchemy.dialects.postgresql import JSONB
from sqlalchemy.orm import Mapped, mapped_column

from app.modelos.base import Base, ConId, ConMarcasDeTiempo

ESTADOS_OPERACION = ("en_curso", "completada")


class OperacionIdempotente(ConId, ConMarcasDeTiempo, Base):
    """Una fila por (negocio, clave). Genérica: sirve a movimientos, recepciones y facturas."""

    __tablename__ = "operaciones_idempotentes"
    __table_args__ = (
        sa.UniqueConstraint("negocio_id", "clave"),
        sa.CheckConstraint("estado in ('en_curso', 'completada')", name="estado_valido"),
    )

    negocio_id: Mapped[uuid.UUID] = mapped_column(
        sa.ForeignKey("negocios.id", ondelete="CASCADE"), nullable=False
    )
    clave: Mapped[str] = mapped_column(sa.String(255), nullable=False)
    endpoint: Mapped[str] = mapped_column(sa.Text, nullable=False)
    hash_peticion: Mapped[str] = mapped_column(sa.Text, nullable=False)
    estado: Mapped[str] = mapped_column(sa.Text, nullable=False, server_default="en_curso")
    status_http: Mapped[int | None] = mapped_column(sa.SmallInteger)
    respuesta: Mapped[dict[str, Any] | None] = mapped_column(JSONB)
