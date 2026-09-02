"""imagenes y FK desde productos.imagen_id

Revision ID: 0016
Revises: 0015
Create Date: 2026-09-02

RF-CAT-006, RNF-05, RNF-11.
"""

from collections.abc import Sequence

import sqlalchemy as sa
from alembic import op

revision: str = "0016"
down_revision: str | None = "0015"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.create_table(
        "imagenes",
        sa.Column("id", sa.Uuid(), server_default=sa.text("gen_random_uuid()"), nullable=False),
        sa.Column("negocio_id", sa.Uuid(), nullable=False),
        sa.Column("tipo", sa.Text(), nullable=False),
        sa.Column("clave_almacenamiento", sa.String(length=128), nullable=False),
        sa.Column("identificador", sa.String(length=32), nullable=False),
        sa.Column("mime", sa.Text(), nullable=False),
        sa.Column("bytes", sa.Integer(), nullable=False),
        sa.Column("ancho", sa.Integer(), nullable=False),
        sa.Column("alto", sa.Integer(), nullable=False),
        sa.Column("checksum", sa.String(length=64), nullable=False),
        sa.Column(
            "created_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False
        ),
        sa.CheckConstraint("tipo in ('producto', 'factura')", name="tipo_valido"),
        sa.ForeignKeyConstraint(
            ["negocio_id"],
            ["negocios.id"],
            name="fk_imagenes_negocio_id_negocios",
            ondelete="CASCADE",
        ),
        sa.PrimaryKeyConstraint("id", name="pk_imagenes"),
        sa.UniqueConstraint("clave_almacenamiento", name="uq_imagenes_clave_almacenamiento"),
        sa.UniqueConstraint("identificador", name="uq_imagenes_identificador"),
    )
    op.create_index("ix_imagenes_negocio_id", "imagenes", ["negocio_id"])
    op.create_foreign_key(
        "fk_productos_imagen_id_imagenes",
        "productos",
        "imagenes",
        ["imagen_id"],
        ["id"],
        ondelete="SET NULL",
    )


def downgrade() -> None:
    op.drop_constraint("fk_productos_imagen_id_imagenes", "productos", type_="foreignkey")
    op.drop_index("ix_imagenes_negocio_id", table_name="imagenes")
    op.drop_table("imagenes")
