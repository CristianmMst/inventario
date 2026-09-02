"""codigos_barras

Revision ID: 0010
Revises: 0009
Create Date: 2026-09-02

RF-CAT-003, RN-05, RNF-01.
"""

from collections.abc import Sequence

import sqlalchemy as sa
from alembic import op

revision: str = "0010"
down_revision: str | None = "0009"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.create_table(
        "codigos_barras",
        sa.Column("id", sa.Uuid(), server_default=sa.text("gen_random_uuid()"), nullable=False),
        sa.Column("negocio_id", sa.Uuid(), nullable=False),
        sa.Column("producto_id", sa.Uuid(), nullable=False),
        sa.Column("codigo", sa.String(length=64), nullable=False),
        sa.Column(
            "created_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False
        ),
        sa.ForeignKeyConstraint(
            ["negocio_id"],
            ["negocios.id"],
            name="fk_codigos_barras_negocio_id_negocios",
            ondelete="CASCADE",
        ),
        sa.ForeignKeyConstraint(
            ["producto_id"],
            ["productos.id"],
            name="fk_codigos_barras_producto_id_productos",
            ondelete="CASCADE",
        ),
        sa.PrimaryKeyConstraint("id", name="pk_codigos_barras"),
        sa.UniqueConstraint("negocio_id", "codigo", name="uq_codigos_barras_negocio_id_codigo"),
    )
    op.create_index("ix_codigos_barras_producto_id", "codigos_barras", ["producto_id"])


def downgrade() -> None:
    op.drop_index("ix_codigos_barras_producto_id", table_name="codigos_barras")
    op.drop_table("codigos_barras")
