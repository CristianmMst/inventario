"""eventos y suscripciones_webhook

Revision ID: 0018
Revises: 0017
Create Date: 2026-09-02

RF-INT-001, RF-INT-004, RF-INT-005, RN-21. `secuencia` es una identidad BIGINT.
"""

from collections.abc import Sequence

import sqlalchemy as sa
from alembic import op
from sqlalchemy.dialects import postgresql

revision: str = "0018"
down_revision: str | None = "0017"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.create_table(
        "eventos",
        sa.Column("id", sa.Uuid(), nullable=False),
        sa.Column("secuencia", sa.BigInteger(), sa.Identity(always=True), nullable=False),
        sa.Column("negocio_id", sa.Uuid(), nullable=False),
        sa.Column("tipo", sa.Text(), nullable=False),
        sa.Column("version", sa.SmallInteger(), nullable=False),
        sa.Column("ocurrido_en", sa.DateTime(timezone=True), nullable=False),
        sa.Column("autor_tipo", sa.Text(), nullable=False),
        sa.Column("autor_id", sa.Uuid(), nullable=False),
        sa.Column("autor_nombre", sa.Text(), nullable=False),
        sa.Column("payload", postgresql.JSONB(astext_type=sa.Text()), nullable=False),
        sa.Column(
            "created_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False
        ),
        sa.ForeignKeyConstraint(
            ["negocio_id"],
            ["negocios.id"],
            name="fk_eventos_negocio_id_negocios",
            ondelete="RESTRICT",
        ),
        sa.PrimaryKeyConstraint("id", name="pk_eventos"),
        sa.UniqueConstraint("secuencia", name="uq_eventos_secuencia"),
    )
    op.create_index("ix_eventos_negocio_secuencia", "eventos", ["negocio_id", "secuencia"])
    op.create_index(
        "ix_eventos_negocio_tipo_secuencia", "eventos", ["negocio_id", "tipo", "secuencia"]
    )
    op.create_table(
        "suscripciones_webhook",
        sa.Column("id", sa.Uuid(), server_default=sa.text("gen_random_uuid()"), nullable=False),
        sa.Column("negocio_id", sa.Uuid(), nullable=False),
        sa.Column("url", sa.Text(), nullable=False),
        sa.Column("tipos", postgresql.ARRAY(sa.Text()), nullable=False),
        sa.Column("secreto_hash", sa.Text(), nullable=False),
        sa.Column("activa", sa.Boolean(), server_default=sa.true(), nullable=False),
        sa.Column("descripcion", sa.Text(), nullable=True),
        sa.Column(
            "created_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False
        ),
        sa.Column(
            "updated_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False
        ),
        sa.ForeignKeyConstraint(
            ["negocio_id"],
            ["negocios.id"],
            name="fk_suscripciones_webhook_negocio_id_negocios",
            ondelete="CASCADE",
        ),
        sa.PrimaryKeyConstraint("id", name="pk_suscripciones_webhook"),
    )
    op.create_index("ix_suscripciones_webhook_negocio_id", "suscripciones_webhook", ["negocio_id"])


def downgrade() -> None:
    op.drop_index("ix_suscripciones_webhook_negocio_id", table_name="suscripciones_webhook")
    op.drop_table("suscripciones_webhook")
    op.drop_index("ix_eventos_negocio_tipo_secuencia", table_name="eventos")
    op.drop_index("ix_eventos_negocio_secuencia", table_name="eventos")
    op.drop_table("eventos")
