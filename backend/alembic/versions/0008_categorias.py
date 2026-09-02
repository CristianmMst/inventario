"""categorias

Revision ID: 0008
Revises: 0007
Create Date: 2026-09-02

RF-CAT-005.
"""

from collections.abc import Sequence

import sqlalchemy as sa
from alembic import op
from sqlalchemy.dialects import postgresql

revision: str = "0008"
down_revision: str | None = "0007"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.create_table(
        "categorias",
        sa.Column("id", sa.Uuid(), server_default=sa.text("gen_random_uuid()"), nullable=False),
        sa.Column("negocio_id", sa.Uuid(), nullable=False),
        sa.Column("nombre", postgresql.CITEXT(), nullable=False),
        sa.Column(
            "created_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False
        ),
        sa.Column(
            "updated_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False
        ),
        sa.PrimaryKeyConstraint("id", name="pk_categorias"),
        sa.ForeignKeyConstraint(
            ["negocio_id"],
            ["negocios.id"],
            name="fk_categorias_negocio_id_negocios",
            ondelete="CASCADE",
        ),
        sa.UniqueConstraint("negocio_id", "nombre", name="uq_categorias_negocio_id_nombre"),
    )
    op.create_index("ix_categorias_negocio_id", "categorias", ["negocio_id"])


def downgrade() -> None:
    op.drop_index("ix_categorias_negocio_id", table_name="categorias")
    op.drop_table("categorias")
