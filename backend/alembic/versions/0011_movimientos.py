"""movimientos con trigger de inmutabilidad

Revision ID: 0011
Revises: 0010
Create Date: 2026-09-02

RF-INV-002, RF-INV-007, RN-02, RNF-13. El trigger rechaza UPDATE y DELETE a nivel de base;
la única escritura posterior permitida es fijar `anulado_en` cuando aún es nulo.
"""

from collections.abc import Sequence

import sqlalchemy as sa
from alembic import op

revision: str = "0011"
down_revision: str | None = "0010"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None

FUNCION = """
CREATE FUNCTION movimientos_inmutables() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'movimiento inmutable: no se borra (RN-02)'
            USING ERRCODE = 'restrict_violation';
    END IF;
    IF OLD.anulado_en IS NULL AND NEW.anulado_en IS NOT NULL
       AND to_jsonb(NEW) - 'anulado_en' = to_jsonb(OLD) - 'anulado_en' THEN
        RETURN NEW;
    END IF;
    RAISE EXCEPTION 'movimiento inmutable: solo se puede fijar anulado_en una vez (RN-02)'
        USING ERRCODE = 'restrict_violation';
END;
$$ LANGUAGE plpgsql;
"""


def upgrade() -> None:
    op.create_table(
        "movimientos",
        sa.Column("id", sa.Uuid(), server_default=sa.text("gen_random_uuid()"), nullable=False),
        sa.Column("negocio_id", sa.Uuid(), nullable=False),
        sa.Column("producto_id", sa.Uuid(), nullable=False),
        sa.Column("tipo", sa.Text(), nullable=False),
        sa.Column("cantidad", sa.Numeric(precision=14, scale=3), nullable=False),
        sa.Column("direccion", sa.SmallInteger(), nullable=False),
        sa.Column("motivo", sa.String(length=32), nullable=False),
        sa.Column("nota", sa.Text(), nullable=True),
        sa.Column("forzado", sa.Boolean(), server_default=sa.false(), nullable=False),
        sa.Column("stock_resultante", sa.Numeric(precision=14, scale=3), nullable=False),
        sa.Column("anulado_en", sa.DateTime(timezone=True), nullable=True),
        sa.Column("anula_movimiento_id", sa.Uuid(), nullable=True),
        sa.Column("recepcion_linea_id", sa.Uuid(), nullable=True),
        sa.Column("origen", sa.Text(), nullable=False),
        sa.Column("autor_tipo", sa.Text(), nullable=False),
        sa.Column("autor_id", sa.Uuid(), nullable=False),
        sa.Column(
            "ocurrido_en", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False
        ),
        sa.Column(
            "created_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False
        ),
        sa.CheckConstraint("cantidad > 0", name="cantidad_positiva"),
        sa.CheckConstraint("direccion in (-1, 1)", name="direccion_valida"),
        sa.CheckConstraint(
            "tipo in ('entrada', 'salida', 'ajuste', 'merma', 'contramovimiento')",
            name="tipo_valido",
        ),
        sa.CheckConstraint("origen in ('app', 'api', 'recepcion')", name="origen_valido"),
        sa.CheckConstraint("autor_tipo in ('usuario', 'servicio')", name="autor_tipo_valido"),
        sa.ForeignKeyConstraint(
            ["anula_movimiento_id"],
            ["movimientos.id"],
            name="fk_movimientos_anula_movimiento_id_movimientos",
            ondelete="RESTRICT",
        ),
        sa.ForeignKeyConstraint(
            ["negocio_id"],
            ["negocios.id"],
            name="fk_movimientos_negocio_id_negocios",
            ondelete="RESTRICT",
        ),
        sa.ForeignKeyConstraint(
            ["producto_id"],
            ["productos.id"],
            name="fk_movimientos_producto_id_productos",
            ondelete="RESTRICT",
        ),
        sa.PrimaryKeyConstraint("id", name="pk_movimientos"),
    )
    op.create_index(
        "ix_movimientos_producto_ocurrido",
        "movimientos",
        ["producto_id", sa.text("ocurrido_en DESC"), sa.text("id DESC")],
    )
    op.create_index("ix_movimientos_negocio_ocurrido", "movimientos", ["negocio_id", "ocurrido_en"])
    op.execute(FUNCION)
    op.execute(
        "CREATE TRIGGER movimientos_inmutables BEFORE UPDATE OR DELETE ON movimientos"
        " FOR EACH ROW EXECUTE FUNCTION movimientos_inmutables()"
    )


def downgrade() -> None:
    op.execute("DROP TRIGGER movimientos_inmutables ON movimientos")
    op.execute("DROP FUNCTION movimientos_inmutables()")
    op.drop_index("ix_movimientos_negocio_ocurrido", table_name="movimientos")
    op.drop_index("ix_movimientos_producto_ocurrido", table_name="movimientos")
    op.drop_table("movimientos")
