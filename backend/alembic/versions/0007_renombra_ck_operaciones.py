"""renombra el CHECK de operaciones_idempotentes

Revision ID: 0007
Revises: 0006
Create Date: 2026-09-02

La 0005 nombró el CHECK con el prefijo ya puesto y Alembic aplicó encima la convención de
nombres, dejando un nombre doble que SQLAlchemy truncó a 63 caracteres con hash.
Una migración aplicada no se edita (constitution.md §4): se corrige con esta.
"""

from collections.abc import Sequence

from alembic import op

revision: str = "0007"
down_revision: str | None = "0006"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None

MAL = "ck_operaciones_idempotentes_ck_operaciones_idempotentes_a94f"  # truncado por SQLAlchemy
BIEN = "ck_operaciones_idempotentes_estado_valido"


def upgrade() -> None:
    op.execute(f"ALTER TABLE operaciones_idempotentes RENAME CONSTRAINT {MAL} TO {BIEN}")


def downgrade() -> None:
    op.execute(f"ALTER TABLE operaciones_idempotentes RENAME CONSTRAINT {BIEN} TO {MAL}")
