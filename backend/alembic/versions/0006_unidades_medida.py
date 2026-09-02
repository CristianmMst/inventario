"""unidades_medida con semilla

Revision ID: 0006
Revises: 0005
Create Date: 2026-09-02

RF-CAT-004, RF-INV-009, RN-06. La semilla va en la migración para que una base recién
creada sea utilizable (plan.md §3.4). Ampliarla es otra migración de datos.
"""

from collections.abc import Sequence

import sqlalchemy as sa
from alembic import op

revision: str = "0006"
down_revision: str | None = "0005"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None

SEMILLA = [
    ("unidad", "Unidad", "discreta", 0, 10),
    ("caja", "Caja", "discreta", 0, 20),
    ("paquete", "Paquete", "discreta", 0, 30),
    ("kg", "Kilogramo", "continua", 3, 40),
    ("g", "Gramo", "continua", 3, 50),
    ("m", "Metro", "continua", 3, 60),
    ("l", "Litro", "continua", 3, 70),
]


def upgrade() -> None:
    tabla = op.create_table(
        "unidades_medida",
        sa.Column("codigo", sa.String(length=16), nullable=False),
        sa.Column("nombre", sa.Text(), nullable=False),
        sa.Column("tipo", sa.Text(), nullable=False),
        sa.Column("decimales", sa.SmallInteger(), nullable=False),
        sa.Column("orden", sa.SmallInteger(), nullable=False),
        sa.CheckConstraint("tipo in ('discreta', 'continua')", name="tipo_valido"),
        sa.CheckConstraint("decimales between 0 and 3", name="decimales_rango"),
        sa.CheckConstraint("tipo <> 'discreta' or decimales = 0", name="discreta_sin_decimales"),
        sa.PrimaryKeyConstraint("codigo", name="pk_unidades_medida"),
    )
    op.bulk_insert(
        tabla,
        [
            {"codigo": c, "nombre": n, "tipo": t, "decimales": d, "orden": o}
            for c, n, t, d, o in SEMILLA
        ],
    )


def downgrade() -> None:
    op.drop_table("unidades_medida")
