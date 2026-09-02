"""Base de pruebas: PostgreSQL real en contenedor (constitution.md §5). Nunca una base embebida."""

import asyncio
import os
import tempfile
from collections.abc import Iterator

import pytest
from testcontainers.community.postgres import PostgresContainer

# Catálogos globales sembrados por migración: no se vacían entre tests.
TABLAS_SEMILLA = {"unidades_medida", "motivos_movimiento"}


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
        os.environ["IMAGENES_DIR"] = tempfile.mkdtemp(prefix="imagenes-prueba-")
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


@pytest.fixture
async def sesion(motor):  # noqa: ANN001, ANN201
    """Sesión SQLAlchemy directa a la base, para preparar datos o verificar el estado."""
    from app.infra.db import fabrica_sesiones

    async with fabrica_sesiones()() as s:
        yield s


@pytest.fixture
async def cliente(motor):  # noqa: ANN001, ANN201
    """Cliente HTTP contra la app real (ASGI), con la base del contenedor detrás."""
    import httpx

    from app.main import app

    transporte = httpx.ASGITransport(app=app, raise_app_exceptions=False)
    async with httpx.AsyncClient(transport=transporte, base_url="http://test") as c:
        yield c


@pytest.fixture(autouse=True)
async def _limpiar_tablas(request: pytest.FixtureRequest):  # noqa: ANN202
    """Deja la base vacía tras cada test de integración que la usó. No toca el esquema."""
    yield
    if "motor" not in request.fixturenames:
        return
    import sqlalchemy as sa

    from app.infra.db import motor as _motor
    from app.modelos.base import Base

    tablas = [t.name for t in reversed(Base.metadata.sorted_tables) if t.name not in TABLAS_SEMILLA]
    if not tablas:
        return
    async with _motor().begin() as con:
        await con.execute(sa.text(f"TRUNCATE {', '.join(tablas)} RESTART IDENTITY CASCADE"))
