"""RF-CAT-003 y RN-05: cero o más códigos por producto; un código pertenece a un solo producto."""

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


async def test_rf_cat_003_el_alta_admite_codigos_de_barras(cliente: httpx.AsyncClient) -> None:
    auth = await _auth(cliente)
    p = await _producto(cliente, auth, codigos_barras=["7701234567890", "7700000000001"])
    assert p["codigos_barras"] == ["7700000000001", "7701234567890"]


async def test_rf_cat_003_asignar_y_quitar_codigos_despues_del_alta(
    cliente: httpx.AsyncClient,
) -> None:
    auth = await _auth(cliente)
    p = await _producto(cliente, auth)
    alta = await cliente.post(
        f"/api/v1/productos/{p['id']}/codigos-barras",
        json={"codigo": "7701234567890"},
        headers=auth,
    )
    assert alta.status_code == 201, alta.text
    assert alta.json()["codigos_barras"] == ["7701234567890"]

    baja = await cliente.delete(
        f"/api/v1/productos/{p['id']}/codigos-barras/7701234567890", headers=auth
    )
    assert baja.status_code == 204
    ficha = await cliente.get(f"/api/v1/productos/{p['id']}", headers=auth)
    assert ficha.json()["codigos_barras"] == []


async def test_rn_05_un_codigo_ya_usado_responde_409_indicando_a_que_producto_pertenece(
    cliente: httpx.AsyncClient,
) -> None:
    auth = await _auth(cliente)
    dueno = await _producto(cliente, auth, nombre="Cuaderno rayado", codigos_barras=["7701"])
    otro = await _producto(cliente, auth, nombre="Cuaderno cuadriculado")
    respuesta = await cliente.post(
        f"/api/v1/productos/{otro['id']}/codigos-barras", json={"codigo": "7701"}, headers=auth
    )
    assert respuesta.status_code == 409
    error = respuesta.json()["error"]
    assert error["code"] == "CODIGO_BARRAS_DUPLICADO"
    assert error["details"]["producto_id"] == dueno["id"]
    assert error["details"]["producto_nombre"] == "Cuaderno rayado"
    assert error["details"]["codigo"] == "7701"


async def test_rn_05_el_alta_con_un_codigo_ya_usado_no_crea_el_producto(
    cliente: httpx.AsyncClient,
) -> None:
    auth = await _auth(cliente)
    await _producto(cliente, auth, codigos_barras=["7701"])
    respuesta = await cliente.post(
        "/api/v1/productos",
        json={
            "nombre": "Otro",
            "unidad_codigo": "unidad",
            "sku": "OTRO",
            "codigos_barras": ["7701"],
        },
        headers=auth,
    )
    assert respuesta.status_code == 409
    assert respuesta.json()["error"]["code"] == "CODIGO_BARRAS_DUPLICADO"


async def test_rn_05_el_mismo_codigo_en_otro_negocio_se_acepta(cliente: httpx.AsyncClient) -> None:
    a, b = await _auth(cliente), await _auth(cliente)
    await _producto(cliente, a, codigos_barras=["7701"])
    p = await _producto(cliente, b, codigos_barras=["7701"])
    assert p["codigos_barras"] == ["7701"]


async def test_rf_cat_003_quitar_un_codigo_que_no_tiene_responde_404(
    cliente: httpx.AsyncClient,
) -> None:
    auth = await _auth(cliente)
    p = await _producto(cliente, auth)
    respuesta = await cliente.delete(
        f"/api/v1/productos/{p['id']}/codigos-barras/999", headers=auth
    )
    assert respuesta.status_code == 404
    assert respuesta.json()["error"]["code"] == "CODIGO_BARRAS_NO_ENCONTRADO"


async def test_rf_cat_003_el_codigo_se_recorta_y_no_puede_estar_vacio(
    cliente: httpx.AsyncClient,
) -> None:
    auth = await _auth(cliente)
    p = await _producto(cliente, auth)
    vacio = await cliente.post(
        f"/api/v1/productos/{p['id']}/codigos-barras", json={"codigo": "  "}, headers=auth
    )
    assert vacio.status_code == 422
    con_espacios = await cliente.post(
        f"/api/v1/productos/{p['id']}/codigos-barras", json={"codigo": " 7701 "}, headers=auth
    )
    assert con_espacios.json()["codigos_barras"] == ["7701"]
