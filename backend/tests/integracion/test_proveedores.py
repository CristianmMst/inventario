"""RF-COM-001 y RN-17: proveedores; con documentos no se borran, se archivan.

El 409 al eliminar un proveedor con documentos se prueba en T-037, cuando existan órdenes.
"""

import uuid

import httpx

from tests import fabricas


async def _sesion(cliente: httpx.AsyncClient) -> dict:
    cuerpo = {
        "email": fabricas.correo_unico(),
        "password": fabricas.CONTRASENA_VALIDA,
        "nombre": "Marta",
        "negocio": {"nombre": "P", "moneda_base": "COP", "zona_horaria": "UTC"},
    }
    r = await cliente.post("/api/v1/auth/registro", json=cuerpo)
    return {"Authorization": f"Bearer {r.json()['token_acceso']}"}


async def _proveedor(cliente: httpx.AsyncClient, auth: dict, **extra: object) -> dict:
    r = await cliente.post(
        "/api/v1/proveedores", json={"nombre": "Papeles SAS"} | extra, headers=auth
    )
    assert r.status_code == 201, r.text
    return r.json()


async def test_rf_com_001_alta_con_todos_los_campos_y_ficha(cliente: httpx.AsyncClient) -> None:
    auth = await _sesion(cliente)
    p = await _proveedor(
        cliente,
        auth,
        identificacion_fiscal="900123456-7",
        contacto="Julio",
        telefono="3001234567",
        email="ventas@papeles.co",
        direccion="Calle 1 # 2-3",
        notas="Entrega los martes",
    )
    assert p["estado"] == "activo"
    ficha = await cliente.get(f"/api/v1/proveedores/{p['id']}", headers=auth)
    assert ficha.status_code == 200
    assert ficha.json()["contacto"] == "Julio" and ficha.json()["email"] == "ventas@papeles.co"


async def test_rf_com_001_el_nombre_es_obligatorio(cliente: httpx.AsyncClient) -> None:
    auth = await _sesion(cliente)
    r = await cliente.post("/api/v1/proveedores", json={"telefono": "1"}, headers=auth)
    assert r.status_code == 422
    assert {c["campo"] for c in r.json()["error"]["details"]["campos"]} == {"nombre"}


async def test_rf_com_001_edicion_parcial(cliente: httpx.AsyncClient) -> None:
    auth = await _sesion(cliente)
    p = await _proveedor(cliente, auth, telefono="1")
    r = await cliente.patch(f"/api/v1/proveedores/{p['id']}", json={"telefono": "2"}, headers=auth)
    assert r.status_code == 200
    assert r.json()["telefono"] == "2" and r.json()["nombre"] == "Papeles SAS"


async def test_rf_com_001_listado_paginado_por_nombre_solo_activos_por_defecto(
    cliente: httpx.AsyncClient,
) -> None:
    auth = await _sesion(cliente)
    for nombre in ["Zeta", "Alfa", "Beta"]:
        await _proveedor(cliente, auth, nombre=nombre)
    archivado = await _proveedor(cliente, auth, nombre="Archivado SA")
    r = await cliente.post(f"/api/v1/proveedores/{archivado['id']}/archivar", headers=auth)
    assert r.status_code == 200 and r.json()["estado"] == "archivado"

    listado = await cliente.get("/api/v1/proveedores", params={"limit": 2}, headers=auth)
    assert [p["nombre"] for p in listado.json()["datos"]] == ["Alfa", "Beta"]
    assert listado.json()["tiene_mas"]
    resto = await cliente.get(
        "/api/v1/proveedores",
        params={"limit": 2, "cursor": listado.json()["cursor_siguiente"]},
        headers=auth,
    )
    assert [p["nombre"] for p in resto.json()["datos"]] == ["Zeta"]
    archivados = await cliente.get(
        "/api/v1/proveedores", params={"estado": "archivado"}, headers=auth
    )
    assert [p["nombre"] for p in archivados.json()["datos"]] == ["Archivado SA"]


async def test_rn_17_un_proveedor_sin_documentos_se_puede_eliminar(
    cliente: httpx.AsyncClient,
) -> None:
    auth = await _sesion(cliente)
    p = await _proveedor(cliente, auth)
    r = await cliente.delete(f"/api/v1/proveedores/{p['id']}", headers=auth)
    assert r.status_code == 204
    assert (await cliente.get(f"/api/v1/proveedores/{p['id']}", headers=auth)).status_code == 404


async def test_rf_com_001_desarchivar(cliente: httpx.AsyncClient) -> None:
    auth = await _sesion(cliente)
    p = await _proveedor(cliente, auth)
    await cliente.post(f"/api/v1/proveedores/{p['id']}/archivar", headers=auth)
    r = await cliente.post(f"/api/v1/proveedores/{p['id']}/desarchivar", headers=auth)
    assert r.status_code == 200 and r.json()["estado"] == "activo"


async def test_rf_aut_007_el_proveedor_de_otro_negocio_no_existe(
    cliente: httpx.AsyncClient,
) -> None:
    a, b = await _sesion(cliente), await _sesion(cliente)
    p = await _proveedor(cliente, a)
    assert (await cliente.get(f"/api/v1/proveedores/{p['id']}", headers=b)).status_code == 404
    assert (
        await cliente.patch(f"/api/v1/proveedores/{p['id']}", json={"notas": "x"}, headers=b)
    ).status_code == 404
    assert (await cliente.delete(f"/api/v1/proveedores/{p['id']}", headers=b)).status_code == 404
    assert (await cliente.get(f"/api/v1/proveedores/{uuid.uuid4()}", headers=a)).json()["error"][
        "code"
    ] == "PROVEEDOR_NO_ENCONTRADO"
