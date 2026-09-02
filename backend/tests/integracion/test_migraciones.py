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
