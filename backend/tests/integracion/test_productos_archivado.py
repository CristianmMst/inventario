"""RF-CAT-011 y RN-17: archivar y desarchivar; el borrado no existe.

Que un archivado no admita movimientos y conserve su historial se prueba en H3 (T-029/T-034).
"""

import httpx

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


async def test_rf_cat_011_archivar_cambia_el_estado_y_desarchivar_lo_devuelve(
    cliente: httpx.AsyncClient,
) -> None:
    auth = await _auth(cliente)
    p = await _producto(cliente, auth)
    archivado = await cliente.post(f"/api/v1/productos/{p['id']}/archivar", headers=auth)
    assert archivado.status_code == 200, archivado.text
    assert archivado.json()["estado"] == "archivado"
    ficha = await cliente.get(f"/api/v1/productos/{p['id']}", headers=auth)
    assert ficha.json()["estado"] == "archivado"

    activo = await cliente.post(f"/api/v1/productos/{p['id']}/desarchivar", headers=auth)
    assert activo.status_code == 200
    assert activo.json()["estado"] == "activo"


async def test_rf_cat_011_un_archivado_no_aparece_en_la_busqueda_de_operacion(
    cliente: httpx.AsyncClient,
) -> None:
    auth = await _auth(cliente)
    p = await _producto(cliente, auth, nombre="Cuaderno viejo")
    await cliente.post(f"/api/v1/productos/{p['id']}/archivar", headers=auth)
    busqueda = await cliente.get("/api/v1/productos/buscar", params={"q": "cuad"}, headers=auth)
    assert busqueda.json()["datos"] == []


async def test_rf_cat_011_un_archivado_escaneado_se_devuelve_marcado_como_archivado(
    cliente: httpx.AsyncClient,
) -> None:
    """Un código conocido nunca se trata como desconocido: ofrecer el alta duplicaría el
    producto (RN-14). La app ve `estado` y ofrece desarchivar."""
    auth = await _auth(cliente)
    p = await _producto(cliente, auth, codigos_barras=["7701"])
    await cliente.post(f"/api/v1/productos/{p['id']}/archivar", headers=auth)
    escaneo = await cliente.get("/api/v1/productos/por-codigo/7701", headers=auth)
    assert escaneo.status_code == 200
    assert escaneo.json()["estado"] == "archivado"


async def test_rf_cat_011_archivar_dos_veces_es_idempotente(cliente: httpx.AsyncClient) -> None:
    auth = await _auth(cliente)
    p = await _producto(cliente, auth)
    await cliente.post(f"/api/v1/productos/{p['id']}/archivar", headers=auth)
    otra_vez = await cliente.post(f"/api/v1/productos/{p['id']}/archivar", headers=auth)
    assert otra_vez.status_code == 200
    assert otra_vez.json()["estado"] == "archivado"


async def test_rn_17_no_existe_borrado_de_productos(cliente: httpx.AsyncClient) -> None:
    auth = await _auth(cliente)
    p = await _producto(cliente, auth)
    borrado = await cliente.delete(f"/api/v1/productos/{p['id']}", headers=auth)
    assert borrado.status_code == 405
    assert borrado.json()["error"]["code"] == "METODO_NO_PERMITIDO"
    assert (await cliente.get(f"/api/v1/productos/{p['id']}", headers=auth)).status_code == 200


async def test_rf_aut_007_archivar_un_producto_ajeno_responde_404(
    cliente: httpx.AsyncClient,
) -> None:
    a, b = await _auth(cliente), await _auth(cliente)
    p = await _producto(cliente, a)
    assert (
        await cliente.post(f"/api/v1/productos/{p['id']}/archivar", headers=b)
    ).status_code == 404
