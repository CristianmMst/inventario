"""Chats del asistente (RF-AST-001). Retell guarda la conversación; aquí solo queda a qué negocio
pertenece cada chat, para que las herramientas respondan con los datos de ese negocio (RN-19)."""

import uuid
from datetime import datetime

import sqlalchemy as sa
from sqlalchemy.orm import Mapped, mapped_column

from app.modelos.base import Base


class ChatAsistente(Base):
    __tablename__ = "chats_asistente"
    __table_args__ = (
        sa.CheckConstraint("autor_tipo in ('usuario', 'servicio')", name="autor_tipo_valido"),
    )

    chat_id: Mapped[str] = mapped_column(sa.Text, primary_key=True)
    negocio_id: Mapped[uuid.UUID] = mapped_column(
        sa.ForeignKey("negocios.id", ondelete="CASCADE"), nullable=False, index=True
    )
    autor_tipo: Mapped[str] = mapped_column(sa.Text, nullable=False)
    autor_id: Mapped[uuid.UUID] = mapped_column(sa.Uuid, nullable=False)
    created_at: Mapped[datetime] = mapped_column(
        sa.DateTime(timezone=True), nullable=False, server_default=sa.func.now()
    )
    terminado_en: Mapped[datetime | None] = mapped_column(sa.DateTime(timezone=True))
