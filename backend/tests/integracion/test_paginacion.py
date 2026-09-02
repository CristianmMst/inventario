"""constitution.md §2: toda colección se pagina por cursor. RF-CAT-014."""

import uuid
from datetime import UTC, datetime, timedelta

import httpx
import pytest
import sqlalchemy as sa
from fastapi import APIRouter, Depends
from sqlalchemy.ext.asyncio import AsyncEngine

from app.api.deps import paginacion
from app.dominio import errores as err
from app.infra.paginacion import (
    Pagina,
    ParametrosPagina,
    codificar_cursor,
    decodificar_cursor,
    paginar,
)
from app.main import app

_meta = sa.MetaData()
_tabla = sa.Table(
    "_prueba_paginacion",
    _meta,
    sa.Column("id", sa.Uuid, primary_key=True),
    sa.Column("ocurrido_en", sa.DateTime(timezone=True), nullable=False),
)


async def _pagina(motor: AsyncEngine, parametros: ParametrosPagina) -> Pagina[dict]:
    consulta = sa.select(_tabla).order_by(_tabla.c.ocurrido_en.desc(), _tabla.c.id.desc())
    if parametros.cursor:
        c = decodificar_cursor(parametros.cursor)
        consulta = consulta.where(
            sa.tuple_(_tabla.c.ocurrido_en, _tabla.c.id)
            < sa.tuple_(datetime.fromisoformat(c["o"]), uuid.UUID(c["id"]))
        )
    async with motor.connect() as con:
        filas = (await con.execute(consulta.limit(parametros.limit + 1))).mappings().all()
    return paginar(
        [dict(f) for f in filas],
        parametros.limit,
        clave_de=lambda f: {"o": f["ocurrido_en"].isoformat(), "id": str(f["id"])},
    )


async def _insertar(motor: AsyncEngine, momentos: list[datetime]) -> list[uuid.UUID]:
    ids = [uuid.uuid4() for _ in momentos]
    async with motor.begin() as con:
        await con.execute(
            _tabla.insert(),
            [{"id": i, "ocurrido_en": m} for i, m in zip(ids, momentos, strict=True)],
        )
    return ids


@pytest.fixture
async def tabla(motor: AsyncEngine):  # noqa: ANN201
    async with motor.begin() as con:
        await con.run_sync(_meta.create_all)
    yield _tabla
    async with motor.begin() as con:
        await con.run_sync(_meta.drop_all)


async def test_rf_cat_014_insertar_entre_dos_paginas_no_duplica_ni_salta(
    motor: AsyncEngine, tabla: sa.Table
) -> None:
    base = datetime(2026, 9, 1, 12, 0, tzinfo=UTC)
    originales = await _insertar(motor, [base + timedelta(minutes=i) for i in range(5)])

    primera = await _pagina(motor, ParametrosPagina(limit=2))
    assert primera.tiene_mas and primera.cursor_siguiente
    assert len(primera.datos) == 2

    # Llegan filas nuevas, más recientes y también intercaladas, entre una página y otra.
    await _insertar(motor, [base + timedelta(minutes=10), base + timedelta(minutes=2, seconds=30)])

    segunda = await _pagina(motor, ParametrosPagina(limit=2, cursor=primera.cursor_siguiente))
    tercera = await _pagina(motor, ParametrosPagina(limit=2, cursor=segunda.cursor_siguiente))
    cuarta = await _pagina(motor, ParametrosPagina(limit=2, cursor=tercera.cursor_siguiente))

    vistos = [f["id"] for p in (primera, segunda, tercera, cuarta) for f in p.datos]
    assert len(vistos) == len(set(vistos)), "hay duplicados entre páginas"
    # Los 5 originales se ven todos: la fila intercalada aparece, la más reciente que la
    # primera página no (llegó después y quedó "antes" del cursor), y nada se salta.
    assert set(originales) <= set(vistos)
    assert cuarta.tiene_mas is False and cuarta.cursor_siguiente is None


def test_constitucion_2_cursor_ida_y_vuelta() -> None:
    clave = {"o": "2026-09-01T12:00:00+00:00", "id": "abc"}
    assert decodificar_cursor(codificar_cursor(clave)) == clave


def test_constitucion_2_cursor_corrupto_es_error_de_validacion() -> None:
    with pytest.raises(err.ValidacionInvalida) as info:
        decodificar_cursor("esto-no-es-un-cursor")
    assert info.value.code == "CURSOR_INVALIDO"


_router = APIRouter(prefix="/api/v1/_prueba")


@_router.get("/paginado")
async def _paginado(p: ParametrosPagina = Depends(paginacion)) -> dict[str, object]:
    return {"limit": p.limit, "cursor": p.cursor}


app.include_router(_router)


def _cliente() -> httpx.AsyncClient:
    transporte = httpx.ASGITransport(app=app, raise_app_exceptions=False)
    return httpx.AsyncClient(transport=transporte, base_url="http://test")


async def test_constitucion_2_limit_por_defecto_50_y_maximo_100() -> None:
    async with _cliente() as cliente:
        sin = await cliente.get("/api/v1/_prueba/paginado")
        tope = await cliente.get("/api/v1/_prueba/paginado", params={"limit": 100})
        exceso = await cliente.get("/api/v1/_prueba/paginado", params={"limit": 101})
        cero = await cliente.get("/api/v1/_prueba/paginado", params={"limit": 0})
    assert sin.json() == {"limit": 50, "cursor": None}
    assert tope.status_code == 200
    assert exceso.status_code == 422 and exceso.json()["error"]["code"] == "VALIDACION"
    assert cero.status_code == 422
