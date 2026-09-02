"""proveedores

Revision ID: 0013
Revises: 0012
Create Date: 2026-09-02

RF-COM-001, RN-17.
"""

from collections.abc import Sequence

import sqlalchemy as sa
from alembic import op

revision: str = "0013"
down_revision: str | None = "0012"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.create_table(
        "proveedores",
        sa.Column("id", sa.Uuid(), server_default=sa.text("gen_random_uuid()"), nullable=False),
        sa.Column("negocio_id", sa.Uuid(), nullable=False),
        sa.Column("nombre", sa.Text(), nullable=False),
        sa.Column("identificacion_fiscal", sa.Text(), nullable=True),
        sa.Column("contacto", sa.Text(), nullable=True),
        sa.Column("telefono", sa.Text(), nullable=True),
        sa.Column("email", sa.Text(), nullable=True),
        sa.Column("direccion", sa.Text(), nullable=True),
        sa.Column("notas", sa.Text(), nullable=True),
        sa.Column("estado", sa.Text(), server_default="activo", nullable=False),
        sa.Column(
            "created_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False
        ),
        sa.Column(
            "updated_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False
        ),
        sa.CheckConstraint("estado in ('activo', 'archivado')", name="estado_valido"),
        sa.ForeignKeyConstraint(
            ["negocio_id"],
            ["negocios.id"],
            name="fk_proveedores_negocio_id_negocios",
            ondelete="CASCADE",
        ),
        sa.PrimaryKeyConstraint("id", name="pk_proveedores"),
    )
    op.create_index("ix_proveedores_negocio_id_nombre", "proveedores", ["negocio_id", "nombre"])


def downgrade() -> None:
    op.drop_index("ix_proveedores_negocio_id_nombre", table_name="proveedores")
    op.drop_table("proveedores")
