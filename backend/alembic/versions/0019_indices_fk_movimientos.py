"""índices en claves foráneas de movimientos y recepciones

Revision ID: 0019
Revises: 0018
Create Date: 2026-09-02

Cuatro FK sin índice hacían que borrar o revisar movimientos recorriera la tabla entera por
fila (lo destapó la semilla de 180.000 movimientos de T-096). `anula_movimiento_id` se consulta
al anular (RF-INV-008); `recepcion_id`/`recepcion_linea_id` al corregir una recepción
(RF-COM-012); `recepciones.proveedor_id` en listados y en el resumen de compras (RF-REP-005).
"""

from collections.abc import Sequence

from alembic import op

revision: str = "0019"
down_revision: str | None = "0018"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.create_index("ix_movimientos_anula_movimiento_id", "movimientos", ["anula_movimiento_id"])
    op.create_index("ix_movimientos_recepcion_id", "movimientos", ["recepcion_id"])
    op.create_index("ix_movimientos_recepcion_linea_id", "movimientos", ["recepcion_linea_id"])
    op.create_index("ix_recepciones_proveedor_id", "recepciones", ["proveedor_id"])


def downgrade() -> None:
    op.drop_index("ix_recepciones_proveedor_id", table_name="recepciones")
    op.drop_index("ix_movimientos_recepcion_linea_id", table_name="movimientos")
    op.drop_index("ix_movimientos_recepcion_id", table_name="movimientos")
    op.drop_index("ix_movimientos_anula_movimiento_id", table_name="movimientos")
