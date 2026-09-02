"""operaciones_idempotentes

Revision ID: 0005
Revises: 0004
Create Date: 2026-09-02

RN-20, RNF-06.
"""

from collections.abc import Sequence

import sqlalchemy as sa
from alembic import op
from sqlalchemy.dialects import postgresql

revision: str = "0005"
down_revision: str | None = "0004"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.create_table(
        "operaciones_idempotentes",
        sa.Column("id", sa.Uuid(), server_default=sa.text("gen_random_uuid()"), nullable=False),
        sa.Column("negocio_id", sa.Uuid(), nullable=False),
        sa.Column("clave", sa.String(length=255), nullable=False),
        sa.Column("endpoint", sa.Text(), nullable=False),
        sa.Column("hash_peticion", sa.Text(), nullable=False),
        sa.Column("estado", sa.Text(), server_default="en_curso", nullable=False),
        sa.Column("status_http", sa.SmallInteger(), nullable=True),
        sa.Column("respuesta", postgresql.JSONB(astext_type=sa.Text()), nullable=True),
        sa.Column(
            "created_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False
        ),
        sa.Column(
            "updated_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False
        ),
        sa.CheckConstraint(
            "estado in ('en_curso', 'completada')",
            name="ck_operaciones_idempotentes_estado_valido",
        ),
        sa.ForeignKeyConstraint(
            ["negocio_id"],
            ["negocios.id"],
            name="fk_operaciones_idempotentes_negocio_id_negocios",
            ondelete="CASCADE",
        ),
        sa.PrimaryKeyConstraint("id", name="pk_operaciones_idempotentes"),
        sa.UniqueConstraint(
            "negocio_id", "clave", name="uq_operaciones_idempotentes_negocio_id_clave"
        ),
    )


def downgrade() -> None:
    op.drop_table("operaciones_idempotentes")
