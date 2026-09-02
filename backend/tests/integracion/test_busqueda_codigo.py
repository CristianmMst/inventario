"""RF-CAT-008, RF-CAT-009, RN-14, RNF-01: búsqueda por código exacto; el 404 trae el código."""

import httpx
import sqlalchemy as sa
from sqlalchemy.ext.asyncio import AsyncSession

from tests import fabricas


async def _auth(cliente: httpx.AsyncClient) -> dict[str, str]:
    cuerpo = {
        "email": fabricas.correo_unico(),
        "password": fabricas.CONTRASENA_VALIDA,
        "nombre": "Marta",
        "negocio": {"nombre": "P", "moneda_base": "COP", "zona_horaria": "UTC"},
    }
    r = await cliente.post("/api/v1/auth/registro", json=cuerpo)
    return {"Authorization": f"Bearer {r.json()['token_acceso']}"}


async def _producto(cliente: httpx.AsyncClient, auth: dict[str, str], **extra: object) -> dict:
    cuerpo = {"nombre": "Cuaderno", "unidad_codigo": "unidad"} | extra
    r = await cliente.post("/api/v1/productos", json=cuerpo, headers=auth)
    assert r.status_code == 201, r.text
    return r.json()


async def test_rf_cat_008_el_codigo_exacto_devuelve_el_producto(cliente: httpx.AsyncClient) -> None:
    auth = await _auth(cliente)
    p = await _producto(cliente, auth, codigos_barras=["7701234567890"])
    respuesta = await cliente.get("/api/v1/productos/por-codigo/7701234567890", headers=auth)
    assert respuesta.status_code == 200, respuesta.text
    assert respuesta.json()["id"] == p["id"]
    assert "7701234567890" in respuesta.json()["codigos_barras"]


async def test_rf_cat_008_la_coincidencia_es_exacta(cliente: httpx.AsyncClient) -> None:
    auth = await _auth(cliente)
    await _producto(cliente, auth, codigos_barras=["7701234567890"])
    parcial = await cliente.get("/api/v1/productos/por-codigo/770123456789", headers=auth)
    assert parcial.status_code == 404


async def test_rf_cat_009_un_codigo_desconocido_responde_404_con_el_codigo_en_el_cuerpo(
    cliente: httpx.AsyncClient, sesion: AsyncSession
) -> None:
    auth = await _auth(cliente)
    respuesta = await cliente.get("/api/v1/productos/por-codigo/999888777", headers=auth)
    assert respuesta.status_code == 404
    error = respuesta.json()["error"]
    assert error["code"] == "PRODUCTO_NO_ENCONTRADO"
    assert error["details"] == {"codigo": "999888777"}
    # RN-14: nada se crea solo.
    productos = (await sesion.execute(sa.text("select count(*) from productos"))).scalar_one()
    codigos = (await sesion.execute(sa.text("select count(*) from codigos_barras"))).scalar_one()
    assert (productos, codigos) == (0, 0)


async def test_rf_aut_007_el_codigo_de_otro_negocio_es_desconocido(
    cliente: httpx.AsyncClient,
) -> None:
    a, b = await _auth(cliente), await _auth(cliente)
    await _producto(cliente, a, codigos_barras=["7701"])
    respuesta = await cliente.get("/api/v1/productos/por-codigo/7701", headers=b)
    assert respuesta.status_code == 404
    assert respuesta.json()["error"]["details"] == {"codigo": "7701"}


async def test_rnf_01_la_busqueda_por_codigo_usa_el_indice_unico(sesion: AsyncSession) -> None:
    from app.modelos.identidad import Negocio

    negocio = Negocio(nombre="P", moneda_base="COP", zona_horaria="UTC")
    sesion.add(negocio)
    await sesion.flush()
    await sesion.execute(
        sa.text(
            "insert into productos (negocio_id, sku, nombre, unidad_codigo)"
            " select :n, 'SKU-' || g, 'Producto ' || g, 'unidad' from generate_series(1, 20000) g"
        ),
        {"n": negocio.id},
    )
    await sesion.execute(
        sa.text(
            "insert into codigos_barras (negocio_id, producto_id, codigo)"
            " select negocio_id, id, '770' || sku from productos where negocio_id = :n"
        ),
        {"n": negocio.id},
    )
    await sesion.execute(sa.text("analyze codigos_barras"))
    plan = "\n".join(
        (
            await sesion.execute(
                sa.text(
                    "explain select producto_id from codigos_barras"
                    " where negocio_id = :n and codigo = '770SKU-500'"
                ),
                {"n": negocio.id},
            )
        ).scalars()
    )
    assert "uq_codigos_barras_negocio_id_codigo" in plan, plan


async def test_rn_14_un_codigo_desconocido_nunca_crea_nada(
    cliente: httpx.AsyncClient, sesion: AsyncSession
) -> None:
    auth = await _auth(cliente)
    for codigo in ["111", "222", "333"]:
        r = await cliente.get(f"/api/v1/productos/por-codigo/{codigo}", headers=auth)
        assert r.status_code == 404
    total = (await sesion.execute(sa.text("select count(*) from productos"))).scalar_one()
    assert total == 0
