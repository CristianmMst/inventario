"""recepciones y recepciones_lineas; enlace desde movimientos

Revision ID: 0015
Revises: 0014
Create Date: 2026-09-02

RF-COM-004..007, RF-COM-011, RF-COM-012, RF-INV-014, RN-08, RN-13.
"""

from collections.abc import Sequence

import sqlalchemy as sa
from alembic import op

revision: str = "0015"
down_revision: str | None = "0014"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.create_table(
        "recepciones",
        sa.Column("id", sa.Uuid(), server_default=sa.text("gen_random_uuid()"), nullable=False),
        sa.Column("negocio_id", sa.Uuid(), nullable=False),
        sa.Column("proveedor_id", sa.Uuid(), nullable=False),
        sa.Column("orden_id", sa.Uuid(), nullable=True),
        sa.Column("secuencia", sa.Integer(), nullable=False),
        sa.Column("fecha", sa.Date(), server_default=sa.func.current_date(), nullable=False),
        sa.Column("moneda", sa.CHAR(3), nullable=False),
        sa.Column(
            "tasa_cambio", sa.Numeric(precision=18, scale=8), server_default="1", nullable=False
        ),
        sa.Column("estado", sa.Text(), server_default="borrador", nullable=False),
        sa.Column("notas", sa.Text(), nullable=True),
        sa.Column("confirmada_en", sa.DateTime(timezone=True), nullable=True),
        sa.Column(
            "created_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False
        ),
        sa.Column(
            "updated_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False
        ),
        sa.CheckConstraint(
            "estado in ('borrador', 'confirmada', 'corregida')", name="estado_valido"
        ),
        sa.CheckConstraint("tasa_cambio > 0", name="tasa_positiva"),
        sa.ForeignKeyConstraint(
            ["negocio_id"],
            ["negocios.id"],
            name="fk_recepciones_negocio_id_negocios",
            ondelete="CASCADE",
        ),
        sa.ForeignKeyConstraint(
            ["orden_id"],
            ["ordenes_compra.id"],
            name="fk_recepciones_orden_id_ordenes_compra",
            ondelete="RESTRICT",
        ),
        sa.ForeignKeyConstraint(
            ["proveedor_id"],
            ["proveedores.id"],
            name="fk_recepciones_proveedor_id_proveedores",
            ondelete="RESTRICT",
        ),
        sa.PrimaryKeyConstraint("id", name="pk_recepciones"),
        sa.UniqueConstraint("negocio_id", "secuencia", name="uq_recepciones_negocio_id_secuencia"),
    )
    op.create_index("ix_recepciones_negocio_id_fecha", "recepciones", ["negocio_id", "fecha"])
    op.create_index("ix_recepciones_orden_id", "recepciones", ["orden_id"])
    op.create_table(
        "recepciones_lineas",
        sa.Column("id", sa.Uuid(), server_default=sa.text("gen_random_uuid()"), nullable=False),
        sa.Column("recepcion_id", sa.Uuid(), nullable=False),
        sa.Column("orden_linea_id", sa.Uuid(), nullable=True),
        sa.Column("producto_id", sa.Uuid(), nullable=False),
        sa.Column("posicion", sa.SmallInteger(), nullable=False),
        sa.Column("cantidad_recibida", sa.Numeric(precision=14, scale=3), nullable=False),
        sa.Column("costo_unitario", sa.Numeric(precision=18, scale=4), nullable=False),
        sa.Column("moneda_costo", sa.CHAR(3), nullable=False),
        sa.Column("tasa_cambio", sa.Numeric(precision=18, scale=8), nullable=True),
        sa.Column("costo_unitario_base", sa.Numeric(precision=18, scale=4), nullable=True),
        sa.Column("exceso", sa.Boolean(), server_default=sa.false(), nullable=False),
        sa.CheckConstraint("cantidad_recibida > 0", name="cantidad_positiva"),
        sa.CheckConstraint("costo_unitario >= 0", name="costo_no_negativo"),
        sa.ForeignKeyConstraint(
            ["orden_linea_id"],
            ["ordenes_compra_lineas.id"],
            name="fk_recepciones_lineas_orden_linea_id_ordenes_compra_lineas",
            ondelete="RESTRICT",
        ),
        sa.ForeignKeyConstraint(
            ["producto_id"],
            ["productos.id"],
            name="fk_recepciones_lineas_producto_id_productos",
            ondelete="RESTRICT",
        ),
        sa.ForeignKeyConstraint(
            ["recepcion_id"],
            ["recepciones.id"],
            name="fk_recepciones_lineas_recepcion_id_recepciones",
            ondelete="CASCADE",
        ),
        sa.PrimaryKeyConstraint("id", name="pk_recepciones_lineas"),
        sa.UniqueConstraint(
            "recepcion_id", "producto_id", name="uq_recepciones_lineas_recepcion_id_producto_id"
        ),
    )
    op.add_column("movimientos", sa.Column("recepcion_id", sa.Uuid(), nullable=True))
    op.create_foreign_key(
        "fk_movimientos_recepcion_id_recepciones",
        "movimientos",
        "recepciones",
        ["recepcion_id"],
        ["id"],
        ondelete="RESTRICT",
    )
    op.create_foreign_key(
        "fk_movimientos_recepcion_linea_id_recepciones_lineas",
        "movimientos",
        "recepciones_lineas",
        ["recepcion_linea_id"],
        ["id"],
        ondelete="RESTRICT",
    )


def downgrade() -> None:
    op.drop_constraint(
        "fk_movimientos_recepcion_linea_id_recepciones_lineas", "movimientos", type_="foreignkey"
    )
    op.drop_constraint("fk_movimientos_recepcion_id_recepciones", "movimientos", type_="foreignkey")
    op.drop_column("movimientos", "recepcion_id")
    op.drop_table("recepciones_lineas")
    op.drop_index("ix_recepciones_orden_id", table_name="recepciones")
    op.drop_index("ix_recepciones_negocio_id_fecha", table_name="recepciones")
    op.drop_table("recepciones")
