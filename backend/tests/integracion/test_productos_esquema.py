"""RF-CAT-001, RF-CAT-002, RF-CAT-012, RF-CAT-013, RNF-02: esquema de productos y búsqueda GIN."""

import uuid

import pytest
import sqlalchemy as sa
from sqlalchemy.exc import IntegrityError
from sqlalchemy.ext.asyncio import AsyncSession

from app.modelos.catalogo import Producto
from app.modelos.identidad import Negocio


async def _negocio(sesion: AsyncSession) -> uuid.UUID:
    negocio = Negocio(nombre="P", moneda_base="COP", zona_horaria="UTC")
    sesion.add(negocio)
    await sesion.flush()
    return negocio.id


async def test_rf_cat_002_el_sku_es_unico_por_negocio(sesion: AsyncSession) -> None:
    n = await _negocio(sesion)
    sesion.add(Producto(negocio_id=n, sku="A-1", nombre="Cuaderno", unidad_codigo="unidad"))
    await sesion.flush()
    sesion.add(Producto(negocio_id=n, sku="A-1", nombre="Otro", unidad_codigo="unidad"))
    with pytest.raises(IntegrityError):
        await sesion.flush()
    await sesion.rollback()


async def test_rf_cat_002_el_mismo_sku_en_otro_negocio_se_acepta(sesion: AsyncSession) -> None:
    a, b = await _negocio(sesion), await _negocio(sesion)
    sesion.add_all(
        [
            Producto(negocio_id=a, sku="A-1", nombre="Cuaderno", unidad_codigo="unidad"),
            Producto(negocio_id=b, sku="A-1", nombre="Cuaderno", unidad_codigo="unidad"),
        ]
    )
    await sesion.commit()


async def test_rf_cat_001_la_unidad_debe_existir_en_el_catalogo(sesion: AsyncSession) -> None:
    n = await _negocio(sesion)
    sesion.add(Producto(negocio_id=n, sku="A-1", nombre="Cuaderno", unidad_codigo="fanega"))
    with pytest.raises(IntegrityError):
        await sesion.flush()
    await sesion.rollback()


async def test_rf_cat_013_costo_y_precio_son_numeric_18_4_y_stock_minimo_14_3(
    sesion: AsyncSession,
) -> None:
    filas = (
        await sesion.execute(
            sa.text(
                "select column_name, numeric_precision, numeric_scale"
                " from information_schema.columns where table_name = 'productos'"
                " and column_name in ('costo_actual', 'precio_venta', 'stock_minimo')"
            )
        )
    ).all()
    por_columna = {f.column_name: (f.numeric_precision, f.numeric_scale) for f in filas}
    assert por_columna["costo_actual"] == (18, 4)
    assert por_columna["precio_venta"] == (18, 4)
    assert por_columna["stock_minimo"] == (14, 3)


async def test_rf_cat_011_el_estado_solo_admite_activo_o_archivado(sesion: AsyncSession) -> None:
    n = await _negocio(sesion)
    with pytest.raises(IntegrityError):
        await sesion.execute(
            sa.text(
                "insert into productos (negocio_id, sku, nombre, unidad_codigo, estado)"
                " values (:n, 'X', 'X', 'unidad', 'borrado')"
            ),
            {"n": n},
        )
    await sesion.rollback()


async def test_rnf_02_la_busqueda_por_texto_usa_el_indice_gin(sesion: AsyncSession) -> None:
    n = await _negocio(sesion)
    await sesion.execute(
        sa.text(
            "insert into productos (negocio_id, sku, nombre, unidad_codigo)"
            " select :n, 'SKU-' || g, 'Producto número ' || g, 'unidad'"
            " from generate_series(1, 25000) g"
        ),
        {"n": n},
    )
    await sesion.execute(sa.text("analyze productos"))
    plan = (
        await sesion.execute(
            sa.text(
                "explain select id from productos where negocio_id = :n"
                " and busqueda @@ to_tsquery('espanol_sin_tildes', 'cuad:*')"
            ),
            {"n": n},
        )
    ).scalars()
    texto = "\n".join(plan)
    assert "ix_productos_busqueda" in texto, texto
    assert "Seq Scan on productos" not in texto, texto


async def test_rf_cat_007_la_columna_de_busqueda_ignora_tildes_y_mayusculas(
    sesion: AsyncSession,
) -> None:
    n = await _negocio(sesion)
    sesion.add(Producto(negocio_id=n, sku="L-1", nombre="Lápiz HB", unidad_codigo="unidad"))
    await sesion.flush()
    encontrado = (
        await sesion.execute(
            sa.text(
                "select count(*) from productos where negocio_id = :n"
                " and busqueda @@ to_tsquery('espanol_sin_tildes', 'LAPIZ:*')"
            ),
            {"n": n},
        )
    ).scalar_one()
    assert encontrado == 1
