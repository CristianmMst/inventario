"""motivos_movimiento con semilla y stock_productos

Revision ID: 0012
Revises: 0011
Create Date: 2026-09-02

RF-INV-010 (motivos: lista cerrada por tipo, con `otro` y nota obligatoria),
RF-INV-003 / RF-INV-004 / RN-01 (instantánea de stock, con índice parcial para RF-REP-001).
"""

from collections.abc import Sequence

import sqlalchemy as sa
from alembic import op

revision: str = "0012"
down_revision: str | None = "0011"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None

# (codigo, tipo, etiqueta, exige_nota, orden)
SEMILLA = [
    ("recepcion_compra", "entrada", "Recepción de compra", False, 10),
    ("carga_inicial", "entrada", "Carga inicial", False, 20),
    ("venta", "salida", "Venta", False, 10),
    ("consumo_interno", "salida", "Consumo interno", False, 20),
    ("rotura", "merma", "Rotura", False, 10),
    ("vencimiento", "merma", "Vencimiento", False, 20),
    ("robo", "merma", "Robo", False, 30),
    ("perdida", "merma", "Pérdida", False, 40),
    ("conteo_fisico", "ajuste", "Conteo físico", False, 10),
    ("correccion_carga", "ajuste", "Corrección de carga", False, 20),
    ("anulacion", "contramovimiento", "Anulación", False, 10),
] + [
    ("otro", tipo, "Otro", True, 90)
    for tipo in ("entrada", "salida", "merma", "ajuste", "contramovimiento")
]


def upgrade() -> None:
    motivos = op.create_table(
        "motivos_movimiento",
        sa.Column("codigo", sa.String(length=32), nullable=False),
        sa.Column("tipo_movimiento", sa.Text(), nullable=False),
        sa.Column("etiqueta", sa.Text(), nullable=False),
        sa.Column("exige_nota", sa.Boolean(), nullable=False),
        sa.Column("orden", sa.SmallInteger(), nullable=False),
        sa.CheckConstraint(
            "tipo_movimiento in ('entrada', 'salida', 'ajuste', 'merma', 'contramovimiento')",
            name="tipo_valido",
        ),
        sa.PrimaryKeyConstraint("tipo_movimiento", "codigo", name="pk_motivos_movimiento"),
    )
    op.bulk_insert(
        motivos,
        [
            {"codigo": c, "tipo_movimiento": t, "etiqueta": e, "exige_nota": n, "orden": o}
            for c, t, e, n, o in SEMILLA
        ],
    )
    op.create_foreign_key(
        "fk_movimientos_tipo_motivo_motivos_movimiento",
        "movimientos",
        "motivos_movimiento",
        ["tipo", "motivo"],
        ["tipo_movimiento", "codigo"],
        ondelete="RESTRICT",
    )

    op.create_table(
        "stock_productos",
        sa.Column("producto_id", sa.Uuid(), nullable=False),
        sa.Column("negocio_id", sa.Uuid(), nullable=False),
        sa.Column(
            "cantidad", sa.Numeric(precision=14, scale=3), server_default="0", nullable=False
        ),
        sa.Column("bajo_minimo", sa.Boolean(), server_default=sa.false(), nullable=False),
        sa.Column(
            "actualizado_en",
            sa.DateTime(timezone=True),
            server_default=sa.func.now(),
            nullable=False,
        ),
        sa.ForeignKeyConstraint(
            ["negocio_id"],
            ["negocios.id"],
            name="fk_stock_productos_negocio_id_negocios",
            ondelete="CASCADE",
        ),
        sa.ForeignKeyConstraint(
            ["producto_id"],
            ["productos.id"],
            name="fk_stock_productos_producto_id_productos",
            ondelete="CASCADE",
        ),
        sa.PrimaryKeyConstraint("producto_id", name="pk_stock_productos"),
    )
    op.create_index(
        "ix_stock_bajo_minimo",
        "stock_productos",
        ["negocio_id"],
        postgresql_where=sa.text("bajo_minimo"),
    )


def downgrade() -> None:
    op.drop_index("ix_stock_bajo_minimo", table_name="stock_productos")
    op.drop_table("stock_productos")
    op.drop_constraint(
        "fk_movimientos_tipo_motivo_motivos_movimiento", "movimientos", type_="foreignkey"
    )
    op.drop_table("motivos_movimiento")
