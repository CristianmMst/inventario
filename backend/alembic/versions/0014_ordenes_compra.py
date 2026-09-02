"""ordenes_compra y ordenes_compra_lineas

Revision ID: 0014
Revises: 0013
Create Date: 2026-09-02

RF-COM-002, RF-COM-003. La cantidad pendiente no se guarda: se calcula.
"""

from collections.abc import Sequence

import sqlalchemy as sa
from alembic import op

revision: str = "0014"
down_revision: str | None = "0013"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.create_table(
        "ordenes_compra",
        sa.Column("id", sa.Uuid(), server_default=sa.text("gen_random_uuid()"), nullable=False),
        sa.Column("negocio_id", sa.Uuid(), nullable=False),
        sa.Column("proveedor_id", sa.Uuid(), nullable=False),
        sa.Column("secuencia", sa.Integer(), nullable=False),
        sa.Column("estado", sa.Text(), server_default="borrador", nullable=False),
        sa.Column("fecha_esperada", sa.Date(), nullable=True),
        sa.Column("moneda", sa.CHAR(3), nullable=False),
        sa.Column("notas", sa.Text(), nullable=True),
        sa.Column("motivo_cierre", sa.Text(), nullable=True),
        sa.Column("emitida_en", sa.DateTime(timezone=True), nullable=True),
        sa.Column("cerrada_en", sa.DateTime(timezone=True), nullable=True),
        sa.Column(
            "created_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False
        ),
        sa.Column(
            "updated_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False
        ),
        sa.CheckConstraint(
            "estado in ('borrador', 'emitida', 'parcialmente_recibida', 'recibida',"
            " 'cerrada_con_faltante', 'cancelada')",
            name="estado_valido",
        ),
        sa.ForeignKeyConstraint(
            ["negocio_id"],
            ["negocios.id"],
            name="fk_ordenes_compra_negocio_id_negocios",
            ondelete="CASCADE",
        ),
        sa.ForeignKeyConstraint(
            ["proveedor_id"],
            ["proveedores.id"],
            name="fk_ordenes_compra_proveedor_id_proveedores",
            ondelete="RESTRICT",
        ),
        sa.PrimaryKeyConstraint("id", name="pk_ordenes_compra"),
        sa.UniqueConstraint(
            "negocio_id", "secuencia", name="uq_ordenes_compra_negocio_id_secuencia"
        ),
    )
    op.create_index(
        "ix_ordenes_compra_negocio_id_proveedor_id",
        "ordenes_compra",
        ["negocio_id", "proveedor_id"],
    )
    op.create_table(
        "ordenes_compra_lineas",
        sa.Column("id", sa.Uuid(), server_default=sa.text("gen_random_uuid()"), nullable=False),
        sa.Column("orden_id", sa.Uuid(), nullable=False),
        sa.Column("producto_id", sa.Uuid(), nullable=False),
        sa.Column("posicion", sa.SmallInteger(), nullable=False),
        sa.Column("cantidad_ordenada", sa.Numeric(precision=14, scale=3), nullable=False),
        sa.Column("costo_unitario_estimado", sa.Numeric(precision=18, scale=4), nullable=True),
        sa.CheckConstraint("cantidad_ordenada > 0", name="cantidad_positiva"),
        sa.ForeignKeyConstraint(
            ["orden_id"],
            ["ordenes_compra.id"],
            name="fk_ordenes_compra_lineas_orden_id_ordenes_compra",
            ondelete="CASCADE",
        ),
        sa.ForeignKeyConstraint(
            ["producto_id"],
            ["productos.id"],
            name="fk_ordenes_compra_lineas_producto_id_productos",
            ondelete="RESTRICT",
        ),
        sa.PrimaryKeyConstraint("id", name="pk_ordenes_compra_lineas"),
        sa.UniqueConstraint(
            "orden_id", "producto_id", name="uq_ordenes_compra_lineas_orden_id_producto_id"
        ),
    )


def downgrade() -> None:
    op.drop_table("ordenes_compra_lineas")
    op.drop_index("ix_ordenes_compra_negocio_id_proveedor_id", table_name="ordenes_compra")
    op.drop_table("ordenes_compra")
