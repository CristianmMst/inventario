"""api_keys

Revision ID: 0004
Revises: 0003
Create Date: 2026-09-02

RF-AUT-005, RNF-11.
"""

from collections.abc import Sequence

import sqlalchemy as sa
from alembic import op

revision: str = "0004"
down_revision: str | None = "0003"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.create_table(
        "api_keys",
        sa.Column("id", sa.Uuid(), server_default=sa.text("gen_random_uuid()"), nullable=False),
        sa.Column("negocio_id", sa.Uuid(), nullable=False),
        sa.Column("nombre", sa.Text(), nullable=False),
        sa.Column("prefijo", sa.String(length=8), nullable=False),
        sa.Column("secreto_hash", sa.Text(), nullable=False),
        sa.Column("ultimo_uso_en", sa.DateTime(timezone=True), nullable=True),
        sa.Column("revocado_en", sa.DateTime(timezone=True), nullable=True),
        sa.Column(
            "created_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False
        ),
        sa.PrimaryKeyConstraint("id", name="pk_api_keys"),
        sa.ForeignKeyConstraint(
            ["negocio_id"],
            ["negocios.id"],
            name="fk_api_keys_negocio_id_negocios",
            ondelete="CASCADE",
        ),
        sa.UniqueConstraint("prefijo", name="uq_api_keys_prefijo"),
    )
    op.create_index("ix_api_keys_negocio_id", "api_keys", ["negocio_id"])


def downgrade() -> None:
    op.drop_index("ix_api_keys_negocio_id", table_name="api_keys")
    op.drop_table("api_keys")
