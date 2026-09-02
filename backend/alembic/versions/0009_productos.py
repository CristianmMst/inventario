"""productos con columna generada tsvector e índice GIN

Revision ID: 0009
Revises: 0008
Create Date: 2026-09-02

RF-CAT-001, RF-CAT-002, RF-CAT-012, RF-CAT-013, RNF-02. La configuración de búsqueda
`espanol_sin_tildes` aplica `unaccent` antes del stemming en español, así "lapiz" encuentra
"Lápiz" y "cuad:*" encuentra "Cuaderno".
"""

from collections.abc import Sequence

import sqlalchemy as sa
from alembic import op
from sqlalchemy.dialects import postgresql

revision: str = "0009"
down_revision: str | None = "0008"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None

EXPRESION_BUSQUEDA = (
    "to_tsvector('espanol_sin_tildes'::regconfig, coalesce(nombre, '') || ' ' || coalesce(sku, ''))"
)


def upgrade() -> None:
    op.execute("CREATE EXTENSION IF NOT EXISTS unaccent")
    op.execute("CREATE TEXT SEARCH CONFIGURATION espanol_sin_tildes (COPY = spanish)")
    op.execute(
        "ALTER TEXT SEARCH CONFIGURATION espanol_sin_tildes"
        " ALTER MAPPING FOR hword, hword_part, word, asciiword, asciihword, hword_asciipart"
        " WITH unaccent, spanish_stem"
    )
    op.create_table(
        "productos",
        sa.Column("id", sa.Uuid(), server_default=sa.text("gen_random_uuid()"), nullable=False),
        sa.Column("negocio_id", sa.Uuid(), nullable=False),
        sa.Column("sku", sa.String(length=64), nullable=False),
        sa.Column("nombre", sa.Text(), nullable=False),
        sa.Column("categoria_id", sa.Uuid(), nullable=True),
        sa.Column("unidad_codigo", sa.String(length=16), nullable=False),
        sa.Column("costo_actual", sa.Numeric(precision=18, scale=4), nullable=True),
        sa.Column("precio_venta", sa.Numeric(precision=18, scale=4), nullable=True),
        sa.Column("stock_minimo", sa.Numeric(precision=14, scale=3), nullable=True),
        sa.Column("imagen_id", sa.Uuid(), nullable=True),
        sa.Column("estado", sa.Text(), server_default="activo", nullable=False),
        sa.Column(
            "busqueda",
            postgresql.TSVECTOR(),
            sa.Computed(EXPRESION_BUSQUEDA, persisted=True),
            nullable=True,
        ),
        sa.Column(
            "created_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False
        ),
        sa.Column(
            "updated_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False
        ),
        sa.CheckConstraint("estado in ('activo', 'archivado')", name="estado_valido"),
        sa.CheckConstraint(
            "stock_minimo is null or stock_minimo >= 0", name="stock_minimo_no_negativo"
        ),
        sa.ForeignKeyConstraint(
            ["categoria_id"],
            ["categorias.id"],
            name="fk_productos_categoria_id_categorias",
            ondelete="SET NULL",
        ),
        sa.ForeignKeyConstraint(
            ["negocio_id"],
            ["negocios.id"],
            name="fk_productos_negocio_id_negocios",
            ondelete="CASCADE",
        ),
        sa.ForeignKeyConstraint(
            ["unidad_codigo"],
            ["unidades_medida.codigo"],
            name="fk_productos_unidad_codigo_unidades_medida",
        ),
        sa.PrimaryKeyConstraint("id", name="pk_productos"),
        sa.UniqueConstraint("negocio_id", "sku", name="uq_productos_negocio_id_sku"),
    )
    # fastupdate=off: sin lista pendiente, el planificador usa el GIN también justo después de
    # una carga masiva. La escritura extra es irrelevante a 15.000 movimientos al mes (RNF-02).
    op.create_index(
        "ix_productos_busqueda",
        "productos",
        ["busqueda"],
        postgresql_using="gin",
        postgresql_with={"fastupdate": "off"},
    )
    op.create_index(
        "ix_productos_negocio_id_categoria_id", "productos", ["negocio_id", "categoria_id"]
    )


def downgrade() -> None:
    op.drop_index("ix_productos_negocio_id_categoria_id", table_name="productos")
    op.drop_index("ix_productos_busqueda", table_name="productos")
    op.drop_table("productos")
    op.execute("DROP TEXT SEARCH CONFIGURATION espanol_sin_tildes")
    op.execute("DROP EXTENSION IF EXISTS unaccent")
