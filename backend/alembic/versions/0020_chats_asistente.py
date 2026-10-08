"""chats del asistente

Revision ID: 0020
Revises: 0019
Create Date: 2026-10-07

RF-AST-001, RF-AST-004: a qué negocio pertenece cada chat de Retell.
"""

from collections.abc import Sequence

import sqlalchemy as sa
from alembic import op

revision: str = "0020"
down_revision: str | None = "0019"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.create_table(
        "chats_asistente",
        sa.Column("chat_id", sa.Text(), nullable=False),
        sa.Column("negocio_id", sa.Uuid(), nullable=False),
        sa.Column("autor_tipo", sa.Text(), nullable=False),
        sa.Column("autor_id", sa.Uuid(), nullable=False),
        sa.Column(
            "created_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False
        ),
        sa.Column("terminado_en", sa.DateTime(timezone=True), nullable=True),
        sa.PrimaryKeyConstraint("chat_id", name="pk_chats_asistente"),
        sa.ForeignKeyConstraint(
            ["negocio_id"],
            ["negocios.id"],
            name="fk_chats_asistente_negocio_id_negocios",
            ondelete="CASCADE",
        ),
        sa.CheckConstraint(
            "autor_tipo in ('usuario', 'servicio')", name="ck_chats_asistente_autor_tipo_valido"
        ),
    )
    op.create_index("ix_chats_asistente_negocio_id", "chats_asistente", ["negocio_id"])


def downgrade() -> None:
    op.drop_index("ix_chats_asistente_negocio_id", table_name="chats_asistente")
    op.drop_table("chats_asistente")
