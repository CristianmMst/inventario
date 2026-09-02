"""RF-INV-010: lista cerrada de motivos por tipo, sembrada, con `otro` y nota obligatoria."""

import httpx
import pytest
import sqlalchemy as sa
from sqlalchemy.ext.asyncio import AsyncSession

from app.dominio import errores as err
from app.dominio.movimientos import TipoMovimiento
from app.servicios.motivos import validar_motivo
from tests import fabricas

ESPERADOS = {
    "entrada": {"recepcion_compra", "carga_inicial", "otro"},
    "salida": {"venta", "consumo_interno", "otro"},
    "merma": {"rotura", "vencimiento", "robo", "perdida", "otro"},
    "ajuste": {"conteo_fisico", "correccion_carga", "otro"},
    "contramovimiento": {"anulacion", "otro"},
}


async def test_rf_inv_010_la_semilla_trae_la_lista_de_la_spec_por_tipo(
    sesion: AsyncSession,
) -> None:
    filas = (
        await sesion.execute(sa.text("select tipo_movimiento, codigo from motivos_movimiento"))
    ).all()
    por_tipo: dict[str, set[str]] = {}
    for f in filas:
        por_tipo.setdefault(f.tipo_movimiento, set()).add(f.codigo)
    assert por_tipo == ESPERADOS


async def test_rf_inv_010_otro_exige_nota_en_todos_los_tipos(sesion: AsyncSession) -> None:
    exigen = (
        await sesion.execute(
            sa.text(
                "select tipo_movimiento from motivos_movimiento"
                " where codigo = 'otro' and exige_nota"
            )
        )
    ).scalars()
    assert set(exigen) == set(ESPERADOS)


async def test_rf_inv_010_motivo_fuera_de_la_lista_del_tipo_responde_422(
    sesion: AsyncSession,
) -> None:
    with pytest.raises(err.ValidacionInvalida) as info:
        await validar_motivo(sesion, TipoMovimiento.SALIDA, "rotura", nota=None)
    assert info.value.code == "MOTIVO_INVALIDO"
    assert set(info.value.details["motivos_validos"]) == ESPERADOS["salida"]


async def test_rf_inv_010_otro_sin_nota_responde_422(sesion: AsyncSession) -> None:
    with pytest.raises(err.ValidacionInvalida) as info:
        await validar_motivo(sesion, TipoMovimiento.SALIDA, "otro", nota="   ")
    assert info.value.code == "NOTA_OBLIGATORIA"


async def test_rf_inv_010_otro_con_nota_se_acepta(sesion: AsyncSession) -> None:
    motivo = await validar_motivo(sesion, TipoMovimiento.SALIDA, "otro", nota="Regalo a cliente")
    assert motivo.codigo == "otro" and motivo.exige_nota


@pytest.mark.parametrize("tipo", [TipoMovimiento.AJUSTE, TipoMovimiento.MERMA])
async def test_rf_inv_010_ajustes_y_mermas_exigen_nota_aunque_el_motivo_no(
    sesion: AsyncSession, tipo: TipoMovimiento
) -> None:
    codigo = "conteo_fisico" if tipo is TipoMovimiento.AJUSTE else "rotura"
    with pytest.raises(err.ValidacionInvalida) as info:
        await validar_motivo(sesion, tipo, codigo, nota=None)
    assert info.value.code == "NOTA_OBLIGATORIA"


async def test_rf_inv_010_un_movimiento_forzado_exige_nota(sesion: AsyncSession) -> None:
    with pytest.raises(err.ValidacionInvalida) as info:
        await validar_motivo(sesion, TipoMovimiento.SALIDA, "venta", nota=None, forzado=True)
    assert info.value.code == "NOTA_OBLIGATORIA"


async def test_rf_inv_010_get_motivos_movimiento_los_expone_por_tipo(
    cliente: httpx.AsyncClient,
) -> None:
    cuerpo = {
        "email": fabricas.correo_unico(),
        "password": fabricas.CONTRASENA_VALIDA,
        "nombre": "Marta",
        "negocio": {"nombre": "P", "moneda_base": "COP", "zona_horaria": "UTC"},
    }
    r = await cliente.post("/api/v1/auth/registro", json=cuerpo)
    assert r.status_code == 201, r.text
    auth = {"Authorization": f"Bearer {r.json()['token_acceso']}"}
    respuesta = await cliente.get(
        "/api/v1/motivos-movimiento", params={"tipo": "merma"}, headers=auth
    )
    assert respuesta.status_code == 200, respuesta.text
    datos = respuesta.json()["datos"]
    assert {m["codigo"] for m in datos} == ESPERADOS["merma"]
    assert all(set(m) >= {"codigo", "tipo_movimiento", "etiqueta", "exige_nota"} for m in datos)
    todos = await cliente.get("/api/v1/motivos-movimiento", headers=auth)
    assert len(todos.json()["datos"]) == sum(len(v) for v in ESPERADOS.values())


async def test_rf_inv_010_la_base_impide_un_motivo_desconocido_en_movimientos(
    sesion: AsyncSession,
) -> None:
    import uuid

    from sqlalchemy.exc import IntegrityError

    from app.modelos.catalogo import Producto
    from app.modelos.identidad import Negocio

    negocio = Negocio(nombre="P", moneda_base="COP", zona_horaria="UTC")
    sesion.add(negocio)
    await sesion.flush()
    producto = Producto(negocio_id=negocio.id, sku="A", nombre="X", unidad_codigo="unidad")
    sesion.add(producto)
    await sesion.flush()
    with pytest.raises(IntegrityError):
        await sesion.execute(
            sa.text(
                "insert into movimientos (negocio_id, producto_id, tipo, cantidad, direccion,"
                " motivo, origen, autor_tipo, autor_id, stock_resultante)"
                " values (:n, :p, 'salida', 1, -1, 'capricho', 'api', 'usuario', :a, 0)"
            ),
            {"n": negocio.id, "p": producto.id, "a": uuid.uuid4()},
        )
    await sesion.rollback()
