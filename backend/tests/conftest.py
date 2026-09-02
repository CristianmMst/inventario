"""Base de pruebas: PostgreSQL real en contenedor (constitution.md §5). Nunca SQLite."""

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
