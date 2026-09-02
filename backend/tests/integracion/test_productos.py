"""RF-CAT-001, RF-CAT-002, RF-CAT-010, RF-CAT-013: alta, ficha y edición de producto."""

import uuid

import httpx

from tests import fabricas


async def _auth(cliente: httpx.AsyncClient, moneda: str = "COP") -> dict[str, str]:
    cuerpo = {
        "email": fabricas.correo_unico(),
        "password": fabricas.CONTRASENA_VALIDA,
        "nombre": "Marta",
        "negocio": {"nombre": "P", "moneda_base": moneda, "zona_horaria": "UTC"},
    }
    r = await cliente.post("/api/v1/auth/registro", json=cuerpo)
    return {"Authorization": f"Bearer {r.json()['token_acceso']}"}


def _producto(**extra: object) -> dict:
    return {"nombre": "Cuaderno 100 hojas", "unidad_codigo": "unidad"} | extra


async def _categoria(cliente: httpx.AsyncClient, auth: dict[str, str], nombre: str) -> dict:
    return (await cliente.post("/api/v1/categorias", json={"nombre": nombre}, headers=auth)).json()


async def test_rf_cat_001_alta_con_todos_los_campos(cliente: httpx.AsyncClient) -> None:
    auth = await _auth(cliente)
    categoria = await _categoria(cliente, auth, "Cuadernos")
    respuesta = await cliente.post(
        "/api/v1/productos",
        json=_producto(
            sku="CUAD-100",
            categoria_id=categoria["id"],
            costo_actual={"monto": "2500.50", "moneda": "COP"},
            precio_venta={"monto": "4000", "moneda": "COP"},
            stock_minimo="10",
        ),
        headers=auth,
    )
    assert respuesta.status_code == 201, respuesta.text
    p = respuesta.json()
    assert p["sku"] == "CUAD-100"
    assert p["categoria"] == {"id": categoria["id"], "nombre": "Cuadernos"}
    assert p["unidad"] == {
        "codigo": "unidad",
        "nombre": "Unidad",
        "tipo": "discreta",
        "decimales": 0,
    }
    assert p["costo_actual"] == {"monto": "2500.5000", "moneda": "COP"}
    assert p["precio_venta"] == {"monto": "4000.0000", "moneda": "COP"}
    assert p["stock_minimo"] == "10.000"
    assert p["estado"] == "activo"
    assert p["codigos_barras"] == []


async def test_rf_cat_001_falta_nombre_o_unidad_responde_422_senalando_el_campo(
    cliente: httpx.AsyncClient,
) -> None:
    auth = await _auth(cliente)
    sin_nombre = await cliente.post(
        "/api/v1/productos", json={"unidad_codigo": "unidad"}, headers=auth
    )
    sin_unidad = await cliente.post("/api/v1/productos", json={"nombre": "X"}, headers=auth)
    assert sin_nombre.status_code == 422
    campos = {c["campo"] for c in sin_nombre.json()["error"]["details"]["campos"]}
    assert campos == {"nombre"}
    assert sin_unidad.status_code == 422
    campos = {c["campo"] for c in sin_unidad.json()["error"]["details"]["campos"]}
    assert campos == {"unidad_codigo"}


async def test_rf_cat_004_una_unidad_desconocida_responde_422(cliente: httpx.AsyncClient) -> None:
    auth = await _auth(cliente)
    respuesta = await cliente.post(
        "/api/v1/productos", json=_producto(unidad_codigo="fanega"), headers=auth
    )
    assert respuesta.status_code == 422
    assert respuesta.json()["error"]["code"] == "UNIDAD_DESCONOCIDA"


async def test_rf_cat_002_sku_repetido_responde_409(cliente: httpx.AsyncClient) -> None:
    auth = await _auth(cliente)
    await cliente.post("/api/v1/productos", json=_producto(sku="A-1"), headers=auth)
    repetido = await cliente.post("/api/v1/productos", json=_producto(sku="A-1"), headers=auth)
    assert repetido.status_code == 409
    assert repetido.json()["error"]["code"] == "SKU_DUPLICADO"


async def test_rf_cat_002_sku_ausente_se_genera_y_es_unico(cliente: httpx.AsyncClient) -> None:
    auth = await _auth(cliente)
    a = (await cliente.post("/api/v1/productos", json=_producto(), headers=auth)).json()
    b = (await cliente.post("/api/v1/productos", json=_producto(), headers=auth)).json()
    assert a["sku"] and b["sku"] and a["sku"] != b["sku"]


async def test_rf_cat_013_el_costo_debe_ir_en_la_moneda_base(cliente: httpx.AsyncClient) -> None:
    auth = await _auth(cliente)
    respuesta = await cliente.post(
        "/api/v1/productos",
        json=_producto(costo_actual={"monto": "1.00", "moneda": "USD"}),
        headers=auth,
    )
    assert respuesta.status_code == 422
    assert respuesta.json()["error"]["code"] == "MONEDA_NO_ES_LA_BASE"


async def test_e01_el_monto_no_puede_ser_un_numero_json(cliente: httpx.AsyncClient) -> None:
    auth = await _auth(cliente)
    respuesta = await cliente.post(
        "/api/v1/productos",
        json=_producto(costo_actual={"monto": 12.5, "moneda": "COP"}),
        headers=auth,
    )
    assert respuesta.status_code == 422


async def test_rn_07_el_stock_minimo_respeta_la_unidad(cliente: httpx.AsyncClient) -> None:
    auth = await _auth(cliente)
    respuesta = await cliente.post(
        "/api/v1/productos", json=_producto(stock_minimo="2.5"), headers=auth
    )
    assert respuesta.status_code == 422
    assert respuesta.json()["error"]["code"] == "CANTIDAD_INVALIDA_PARA_UNIDAD"


async def test_rf_cat_008_la_ficha_se_consulta_por_id(cliente: httpx.AsyncClient) -> None:
    auth = await _auth(cliente)
    creado = (
        await cliente.post("/api/v1/productos", json=_producto(sku="A-1"), headers=auth)
    ).json()
    ficha = await cliente.get(f"/api/v1/productos/{creado['id']}", headers=auth)
    assert ficha.status_code == 200
    assert ficha.json()["sku"] == "A-1"


async def test_rf_aut_007_el_producto_de_otro_negocio_no_existe(
    cliente: httpx.AsyncClient,
) -> None:
    a, b = await _auth(cliente), await _auth(cliente)
    creado = (await cliente.post("/api/v1/productos", json=_producto(), headers=a)).json()
    ajeno = await cliente.get(f"/api/v1/productos/{creado['id']}", headers=b)
    assert ajeno.status_code == 404
    assert ajeno.json()["error"]["code"] == "PRODUCTO_NO_ENCONTRADO"
    inexistente = await cliente.get(f"/api/v1/productos/{uuid.uuid4()}", headers=a)
    assert inexistente.status_code == 404


async def test_rf_cat_010_editar_sobrescribe_costo_y_precio_sin_historial(
    cliente: httpx.AsyncClient,
) -> None:
    auth = await _auth(cliente)
    creado = (
        await cliente.post(
            "/api/v1/productos",
            json=_producto(costo_actual={"monto": "100", "moneda": "COP"}),
            headers=auth,
        )
    ).json()
    editado = await cliente.patch(
        f"/api/v1/productos/{creado['id']}",
        json={"costo_actual": {"monto": "120", "moneda": "COP"}, "nombre": "Cuaderno 200 hojas"},
        headers=auth,
    )
    assert editado.status_code == 200, editado.text
    assert editado.json()["costo_actual"] == {"monto": "120.0000", "moneda": "COP"}
    assert editado.json()["nombre"] == "Cuaderno 200 hojas"
    assert editado.json()["sku"] == creado["sku"], "lo no enviado no cambia"


async def test_rf_cat_010_editar_permite_quitar_categoria_y_stock_minimo(
    cliente: httpx.AsyncClient,
) -> None:
    auth = await _auth(cliente)
    categoria = await _categoria(cliente, auth, "C")
    creado = (
        await cliente.post(
            "/api/v1/productos",
            json=_producto(categoria_id=categoria["id"], stock_minimo="5"),
            headers=auth,
        )
    ).json()
    editado = await cliente.patch(
        f"/api/v1/productos/{creado['id']}",
        json={"categoria_id": None, "stock_minimo": None},
        headers=auth,
    )
    assert editado.status_code == 200
    assert editado.json()["categoria"] is None
    assert editado.json()["stock_minimo"] is None


async def test_rf_cat_005_la_categoria_debe_ser_del_negocio(cliente: httpx.AsyncClient) -> None:
    a, b = await _auth(cliente), await _auth(cliente)
    ajena = await _categoria(cliente, b, "C")
    respuesta = await cliente.post(
        "/api/v1/productos", json=_producto(categoria_id=ajena["id"]), headers=a
    )
    assert respuesta.status_code == 422
    assert respuesta.json()["error"]["code"] == "CATEGORIA_DESCONOCIDA"


async def test_rf_cat_002_editar_el_sku_a_uno_ya_usado_responde_409(
    cliente: httpx.AsyncClient,
) -> None:
    auth = await _auth(cliente)
    await cliente.post("/api/v1/productos", json=_producto(sku="A-1"), headers=auth)
    otro = (await cliente.post("/api/v1/productos", json=_producto(sku="A-2"), headers=auth)).json()
    editado = await cliente.patch(
        f"/api/v1/productos/{otro['id']}", json={"sku": "A-1"}, headers=auth
    )
    assert editado.status_code == 409
