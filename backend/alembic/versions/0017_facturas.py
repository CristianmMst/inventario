"""facturas, facturas_recepciones y facturas_imagenes

Revision ID: 0017
Revises: 0016
Create Date: 2026-09-02

RF-FAC-001..006, RN-18. `ck_facturas_cuadre` lo comprueba la base, no solo Pydantic.
"""

from collections.abc import Sequence

import sqlalchemy as sa
from alembic import op

revision: str = "0017"
down_revision: str | None = "0016"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.create_table(
        "facturas",
        sa.Column("id", sa.Uuid(), server_default=sa.text("gen_random_uuid()"), nullable=False),
        sa.Column("negocio_id", sa.Uuid(), nullable=False),
        sa.Column("proveedor_id", sa.Uuid(), nullable=False),
        sa.Column("numero", sa.String(length=64), nullable=False),
        sa.Column("fecha_emision", sa.Date(), nullable=False),
        sa.Column("fecha_vencimiento", sa.Date(), nullable=True),
        sa.Column("moneda", sa.CHAR(3), nullable=False),
        sa.Column("base_gravable", sa.Numeric(precision=18, scale=4), nullable=False),
        sa.Column("impuesto", sa.Numeric(precision=18, scale=4), nullable=False),
        sa.Column("total", sa.Numeric(precision=18, scale=4), nullable=False),
        sa.Column(
            "tasa_cambio", sa.Numeric(precision=18, scale=8), server_default="1", nullable=False
        ),
        sa.Column("total_base", sa.Numeric(precision=18, scale=4), nullable=False),
        sa.Column("estado_pago", sa.Text(), server_default="pendiente", nullable=False),
        sa.Column("fecha_pago", sa.Date(), nullable=True),
        sa.Column("motivo_anulacion", sa.Text(), nullable=True),
        sa.Column("notas", sa.Text(), nullable=True),
        sa.Column(
            "created_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False
        ),
        sa.Column(
            "updated_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False
        ),
        sa.CheckConstraint("base_gravable + impuesto = total", name="cuadre"),
        sa.CheckConstraint(
            "estado_pago in ('pendiente', 'pagada', 'anulada')", name="estado_pago_valido"
        ),
        sa.CheckConstraint("tasa_cambio > 0", name="tasa_positiva"),
        sa.ForeignKeyConstraint(
            ["negocio_id"],
            ["negocios.id"],
            name="fk_facturas_negocio_id_negocios",
            ondelete="CASCADE",
        ),
        sa.ForeignKeyConstraint(
            ["proveedor_id"],
            ["proveedores.id"],
            name="fk_facturas_proveedor_id_proveedores",
            ondelete="RESTRICT",
        ),
        sa.PrimaryKeyConstraint("id", name="pk_facturas"),
        sa.UniqueConstraint(
            "negocio_id",
            "proveedor_id",
            "numero",
            name="uq_facturas_negocio_id_proveedor_id_numero",
        ),
    )
    op.create_index(
        "ix_facturas_negocio_id_fecha_emision", "facturas", ["negocio_id", "fecha_emision"]
    )
    op.create_index("ix_facturas_negocio_id_estado_pago", "facturas", ["negocio_id", "estado_pago"])
    op.create_table(
        "facturas_recepciones",
        sa.Column("factura_id", sa.Uuid(), nullable=False),
        sa.Column("recepcion_id", sa.Uuid(), nullable=False),
        sa.ForeignKeyConstraint(
            ["factura_id"],
            ["facturas.id"],
            name="fk_facturas_recepciones_factura_id_facturas",
            ondelete="CASCADE",
        ),
        sa.ForeignKeyConstraint(
            ["recepcion_id"],
            ["recepciones.id"],
            name="fk_facturas_recepciones_recepcion_id_recepciones",
            ondelete="RESTRICT",
        ),
        sa.PrimaryKeyConstraint("factura_id", "recepcion_id", name="pk_facturas_recepciones"),
        sa.UniqueConstraint("recepcion_id", name="uq_facturas_recepciones_recepcion_id"),
    )
    op.create_table(
        "facturas_imagenes",
        sa.Column("factura_id", sa.Uuid(), nullable=False),
        sa.Column("imagen_id", sa.Uuid(), nullable=False),
        sa.Column("orden", sa.SmallInteger(), nullable=False),
        sa.Column(
            "created_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False
        ),
        sa.ForeignKeyConstraint(
            ["factura_id"],
            ["facturas.id"],
            name="fk_facturas_imagenes_factura_id_facturas",
            ondelete="CASCADE",
        ),
        sa.ForeignKeyConstraint(
            ["imagen_id"],
            ["imagenes.id"],
            name="fk_facturas_imagenes_imagen_id_imagenes",
            ondelete="RESTRICT",
        ),
        sa.PrimaryKeyConstraint("factura_id", "imagen_id", name="pk_facturas_imagenes"),
        sa.UniqueConstraint("factura_id", "orden", name="uq_facturas_imagenes_factura_id_orden"),
        sa.UniqueConstraint("imagen_id", name="uq_facturas_imagenes_imagen_id"),
    )


def downgrade() -> None:
    op.drop_table("facturas_imagenes")
    op.drop_table("facturas_recepciones")
    op.drop_index("ix_facturas_negocio_id_estado_pago", table_name="facturas")
    op.drop_index("ix_facturas_negocio_id_fecha_emision", table_name="facturas")
    op.drop_table("facturas")
