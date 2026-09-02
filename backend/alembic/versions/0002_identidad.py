"""usuarios, negocios y membresias

Revision ID: 0002
Revises: 0001
Create Date: 2026-09-02

RF-AUT-001, RF-AUT-004, RN-19.
"""

from collections.abc import Sequence

import sqlalchemy as sa
from alembic import op
from sqlalchemy.dialects import postgresql

revision: str = "0002"
down_revision: str | None = "0001"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None

rol_membresia = postgresql.ENUM("dueno", name="rol_membresia", create_type=False)


def upgrade() -> None:
    op.execute("CREATE EXTENSION IF NOT EXISTS citext")
    op.execute("CREATE TYPE rol_membresia AS ENUM ('dueno')")

    op.create_table(
        "usuarios",
        sa.Column("id", sa.Uuid(), server_default=sa.text("gen_random_uuid()"), nullable=False),
        sa.Column("email", postgresql.CITEXT(), nullable=False),
        sa.Column("password_hash", sa.Text(), nullable=False),
        sa.Column("nombre", sa.Text(), nullable=False),
        sa.Column(
            "created_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False
        ),
        sa.Column(
            "updated_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False
        ),
        sa.PrimaryKeyConstraint("id", name="pk_usuarios"),
        sa.UniqueConstraint("email", name="uq_usuarios_email"),
    )
    op.create_table(
        "negocios",
        sa.Column("id", sa.Uuid(), server_default=sa.text("gen_random_uuid()"), nullable=False),
        sa.Column("nombre", sa.Text(), nullable=False),
        sa.Column("moneda_base", sa.CHAR(3), nullable=False),
        sa.Column("zona_horaria", sa.Text(), nullable=False),
        sa.Column(
            "created_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False
        ),
        sa.Column(
            "updated_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False
        ),
        sa.PrimaryKeyConstraint("id", name="pk_negocios"),
    )
    op.create_table(
        "membresias",
        sa.Column("id", sa.Uuid(), server_default=sa.text("gen_random_uuid()"), nullable=False),
        sa.Column("usuario_id", sa.Uuid(), nullable=False),
        sa.Column("negocio_id", sa.Uuid(), nullable=False),
        sa.Column("rol", rol_membresia, nullable=False),
        sa.Column(
            "created_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False
        ),
        sa.Column(
            "updated_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False
        ),
        sa.PrimaryKeyConstraint("id", name="pk_membresias"),
        sa.ForeignKeyConstraint(
            ["usuario_id"],
            ["usuarios.id"],
            name="fk_membresias_usuario_id_usuarios",
            ondelete="CASCADE",
        ),
        sa.ForeignKeyConstraint(
            ["negocio_id"],
            ["negocios.id"],
            name="fk_membresias_negocio_id_negocios",
            ondelete="CASCADE",
        ),
        sa.UniqueConstraint("usuario_id", "negocio_id", name="uq_membresias_usuario_id_negocio_id"),
    )
    op.create_index("ix_membresias_negocio_id", "membresias", ["negocio_id"])


def downgrade() -> None:
    op.drop_index("ix_membresias_negocio_id", table_name="membresias")
    op.drop_table("membresias")
    op.drop_table("negocios")
    op.drop_table("usuarios")
    op.execute("DROP TYPE rol_membresia")
