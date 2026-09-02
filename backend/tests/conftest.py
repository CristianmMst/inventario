"""Base de pruebas: PostgreSQL real en contenedor (constitution.md §5). Nunca SQLite."""

import asyncio
import os
from collections.abc import Iterator

import pytest
from testcontainers.community.postgres import PostgresContainer


@pytest.fixture(scope="session")
def postgres_url() -> Iterator[str]:
    """URL asyncpg de un PostgreSQL 16 limpio, uno por sesión de pruebas."""
    with PostgresContainer("postgres:16", driver=None) as contenedor:
        host = contenedor.get_container_host_ip()
        puerto = contenedor.get_exposed_port(5432)
        url = (
            f"postgresql+asyncpg://{contenedor.username}:{contenedor.password}"
            f"@{host}:{puerto}/{contenedor.dbname}"
        )
        os.environ["DATABASE_URL"] = url
        yield url


@pytest.fixture(scope="session")
async def motor(postgres_url: str):  # noqa: ANN201
    """Motor asyncpg contra el contenedor, con el esquema en `head`."""
    from alembic import command
    from alembic.config import Config

    from app.infra import db

    cfg = Config("alembic.ini")
    cfg.set_main_option("sqlalchemy.url", postgres_url)
    await asyncio.to_thread(command.upgrade, cfg, "head")
    await db.reiniciar_motor()
    from app.config import obtener_ajustes

    obtener_ajustes.cache_clear()
    m = db.motor()
    yield m
    await db.reiniciar_motor()
