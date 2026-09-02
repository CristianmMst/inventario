"""constitution.md §4: toda migración tiene downgrade funcional."""

import sqlalchemy as sa
from alembic import command
from alembic.config import Config
from sqlalchemy import create_engine


def _config(url: str) -> Config:
    cfg = Config("alembic.ini")
    cfg.set_main_option("sqlalchemy.url", url)
    return cfg


def _tablas(url: str) -> set[str]:
    motor = create_engine(url.replace("+asyncpg", "+psycopg"))
    with motor.connect() as con:
        filas = con.execute(
            sa.text("SELECT tablename FROM pg_tables WHERE schemaname = 'public'")
        ).scalars()
        return set(filas)


def test_constitucion_4_upgrade_head_y_downgrade_base_sobre_base_limpia(postgres_url: str) -> None:
    cfg = _config(postgres_url)
    command.upgrade(cfg, "head")
    assert "alembic_version" in _tablas(postgres_url)
    command.downgrade(cfg, "base")
    assert _tablas(postgres_url) - {"alembic_version"} == set()
    command.upgrade(cfg, "head")


def test_constitucion_4_modelos_y_migraciones_coinciden(postgres_url: str) -> None:
    """Autogenerate no debe proponer ningún cambio: lo que hay en `head` es lo que
    declaran los modelos."""
    from alembic.autogenerate import compare_metadata
    from alembic.migration import MigrationContext

    import app.modelos  # noqa: F401
    from app.modelos.base import Base

    cfg = _config(postgres_url)
    command.upgrade(cfg, "head")
    motor = create_engine(postgres_url.replace("+asyncpg", "+psycopg"))
    with motor.connect() as con:
        contexto = MigrationContext.configure(con)
        diferencias = compare_metadata(contexto, Base.metadata)
    # Alembic no sabe comparar el texto de un CHECK: los reporta siempre. Se ignoran.
    diferencias = [
        d
        for d in diferencias
        if not (isinstance(d, tuple) and isinstance(d[1], sa.CheckConstraint))
    ]
    assert diferencias == []
