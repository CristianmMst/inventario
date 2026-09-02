"""constitution.md §5: los tests de integración corren contra PostgreSQL real, nunca SQLite."""

import re
from pathlib import Path

import httpx
import sqlalchemy as sa
from sqlalchemy.ext.asyncio import AsyncSession

from tests import fabricas


async def test_constitucion_5_la_sesion_de_pruebas_es_postgresql_16(sesion: AsyncSession) -> None:
    version = (await sesion.execute(sa.text("select version()"))).scalar_one()
    assert version.startswith("PostgreSQL 16")


async def test_constitucion_5_el_cliente_habla_con_la_app_real(cliente: httpx.AsyncClient) -> None:
    respuesta = await cliente.get("/api/v1/salud")
    assert respuesta.status_code == 200
    assert respuesta.headers.get("X-Request-Id")


def test_constitucion_5_ningun_test_usa_sqlite() -> None:
    raiz = Path(__file__).resolve().parents[1]
    culpables = [
        str(p.relative_to(raiz))
        for p in raiz.rglob("*.py")
        if p.name != Path(__file__).name and re.search(r"sqlite", p.read_text("utf-8"), re.I)
    ]
    assert culpables == []


def test_constitucion_5_las_fabricas_generan_valores_unicos() -> None:
    assert fabricas.correo_unico() != fabricas.correo_unico()
    assert fabricas.nombre_unico("Producto") != fabricas.nombre_unico("Producto")
    assert fabricas.nombre_unico("Producto").startswith("Producto ")
