"""RF-INV-002, RF-INV-007, RN-02, RNF-13: movimientos inmutables por trigger en la base."""

import uuid

import pytest
import sqlalchemy as sa
from sqlalchemy.exc import DBAPIError, IntegrityError
from sqlalchemy.ext.asyncio import AsyncSession

from app.modelos.catalogo import Producto
from app.modelos.identidad import Negocio


async def _producto(sesion: AsyncSession) -> tuple[uuid.UUID, uuid.UUID]:
    negocio = Negocio(nombre="P", moneda_base="COP", zona_horaria="UTC")
    sesion.add(negocio)
    await sesion.flush()
    producto = Producto(negocio_id=negocio.id, sku="A", nombre="Cuaderno", unidad_codigo="unidad")
    sesion.add(producto)
    await sesion.flush()
    return negocio.id, producto.id


async def _insertar(
    sesion: AsyncSession, negocio_id: uuid.UUID, producto_id: uuid.UUID
) -> uuid.UUID:
    return (
        await sesion.execute(
            sa.text(
                "insert into movimientos (negocio_id, producto_id, tipo, cantidad, direccion,"
                " motivo, origen, autor_tipo, autor_id, stock_resultante)"
                " values (:n, :p, 'entrada', 5, 1, 'carga_inicial', 'api', 'usuario', :a, 5)"
                " returning id"
            ),
            {"n": negocio_id, "p": producto_id, "a": uuid.uuid4()},
        )
    ).scalar_one()


async def test_rf_inv_007_un_update_directo_por_sql_falla(sesion: AsyncSession) -> None:
    n, p = await _producto(sesion)
    mid = await _insertar(sesion, n, p)
    await sesion.commit()
    with pytest.raises(DBAPIError) as info:
        await sesion.execute(
            sa.text("update movimientos set cantidad = 99 where id = :id"), {"id": mid}
        )
    assert "inmutable" in str(info.value).lower()
    await sesion.rollback()


async def test_rf_inv_007_un_delete_directo_por_sql_falla(sesion: AsyncSession) -> None:
    n, p = await _producto(sesion)
    mid = await _insertar(sesion, n, p)
    await sesion.commit()
    with pytest.raises(DBAPIError):
        await sesion.execute(sa.text("delete from movimientos where id = :id"), {"id": mid})
    await sesion.rollback()


async def test_rn_02_solo_se_puede_fijar_anulado_en_una_vez(sesion: AsyncSession) -> None:
    n, p = await _producto(sesion)
    mid = await _insertar(sesion, n, p)
    await sesion.commit()
    await sesion.execute(
        sa.text("update movimientos set anulado_en = now() where id = :id"), {"id": mid}
    )
    await sesion.commit()
    with pytest.raises(DBAPIError):
        await sesion.execute(
            sa.text("update movimientos set anulado_en = now() + interval '1 day' where id = :id"),
            {"id": mid},
        )
    await sesion.rollback()


async def test_rnf_13_el_rastro_de_autor_y_momento_es_obligatorio(sesion: AsyncSession) -> None:
    n, p = await _producto(sesion)
    with pytest.raises(IntegrityError):
        await sesion.execute(
            sa.text(
                "insert into movimientos (negocio_id, producto_id, tipo, cantidad, direccion,"
                " motivo, origen, autor_tipo, autor_id, stock_resultante)"
                " values (:n, :p, 'entrada', 5, 1, 'carga_inicial', 'api', null, null, 5)"
            ),
            {"n": n, "p": p},
        )
    await sesion.rollback()


async def test_rf_inv_002_ocurrido_en_se_fija_solo_y_en_utc(sesion: AsyncSession) -> None:
    n, p = await _producto(sesion)
    mid = await _insertar(sesion, n, p)
    fila = (
        await sesion.execute(
            sa.text("select ocurrido_en, created_at from movimientos where id = :id"), {"id": mid}
        )
    ).one()
    assert fila.ocurrido_en.tzinfo is not None
    assert fila.ocurrido_en == fila.created_at


@pytest.mark.parametrize(
    "columna_valor",
    ["cantidad, 0", "cantidad, -1", "direccion, 0", "tipo, 'venta'", "origen, 'excel'"],
)
async def test_rn_07_la_base_rechaza_cantidad_direccion_tipo_u_origen_invalidos(
    sesion: AsyncSession, columna_valor: str
) -> None:
    n, p = await _producto(sesion)
    columna, valor = (x.strip() for x in columna_valor.split(","))
    valores = {
        "cantidad": "5",
        "direccion": "1",
        "tipo": "'entrada'",
        "origen": "'api'",
    } | {columna: valor}
    with pytest.raises(IntegrityError):
        await sesion.execute(
            sa.text(
                "insert into movimientos (negocio_id, producto_id, tipo, cantidad, direccion,"
                " motivo, origen, autor_tipo, autor_id, stock_resultante)"
                f" values (:n, :p, {valores['tipo']}, {valores['cantidad']},"
                f" {valores['direccion']}, 'carga_inicial', {valores['origen']}, 'usuario', :a, 5)"
            ),
            {"n": n, "p": p, "a": uuid.uuid4()},
        )
    await sesion.rollback()


async def test_rf_inv_007_el_indice_del_historial_existe(sesion: AsyncSession) -> None:
    definiciones = (
        await sesion.execute(
            sa.text("select indexdef from pg_indexes where tablename = 'movimientos'")
        )
    ).scalars()
    texto = "\n".join(definiciones)
    assert "ix_movimientos_producto_ocurrido" in texto
    assert "ix_movimientos_negocio_ocurrido" in texto
