"""GET /integracion/stock-bajo: los negocios con productos bajo el mínimo, con el email del
dueño, para avisarles desde una integración (n8n). Público por decisión del proyecto."""

import uuid

import httpx

from tests import fabricas


async def _negocio(cliente: httpx.AsyncClient, nombre: str) -> tuple[dict, str]:
    correo = fabricas.correo_unico()
    cuerpo = {
        "email": correo,
        "password": fabricas.CONTRASENA_VALIDA,
        "nombre": f"Dueño de {nombre}",
        "negocio": {"nombre": nombre, "moneda_base": "COP", "zona_horaria": "UTC"},
    }
    r = await cliente.post("/api/v1/auth/registro", json=cuerpo)
    assert r.status_code == 201, r.text
    return {"Authorization": f"Bearer {r.json()['token_acceso']}"}, correo


async def _producto_con_stock(
    cliente: httpx.AsyncClient, auth: dict, nombre: str, minimo: str | None, stock: str
) -> dict:
    cuerpo: dict = {"nombre": nombre, "unidad_codigo": "unidad"}
    if minimo is not None:
        cuerpo["stock_minimo"] = minimo
    r = await cliente.post("/api/v1/productos", json=cuerpo, headers=auth)
    assert r.status_code == 201, r.text
    producto = r.json()
    r = await cliente.post(
        "/api/v1/movimientos",
        json={
            "producto_id": producto["id"],
            "tipo": "entrada",
            "cantidad": stock,
            "motivo": "carga_inicial",
        },
        headers=auth | {"Idempotency-Key": str(uuid.uuid4())},
    )
    assert r.status_code == 201, r.text
    return producto


async def test_lista_por_negocio_los_productos_bajo_minimo_sin_credencial(
    cliente: httpx.AsyncClient,
) -> None:
    auth_a, correo_a = await _negocio(cliente, "Tienda A")
    await _producto_con_stock(cliente, auth_a, "Leve", "10", "8")
    await _producto_con_stock(cliente, auth_a, "Grave", "10", "2")
    await _producto_con_stock(cliente, auth_a, "Sano", "10", "20")
    await _producto_con_stock(cliente, auth_a, "Sin mínimo", None, "1")

    auth_b, correo_b = await _negocio(cliente, "Tienda B")
    await _producto_con_stock(cliente, auth_b, "Arroz", "5", "1")

    auth_c, _ = await _negocio(cliente, "Tienda C")
    await _producto_con_stock(cliente, auth_c, "Todo bien", "5", "50")

    r = await cliente.get("/api/v1/integracion/stock-bajo")

    assert r.status_code == 200, r.text
    negocios = r.json()["negocios"]
    assert [n["negocio"]["nombre"] for n in negocios] == ["Tienda A", "Tienda B"]

    a, b = negocios
    assert a["dueno"] == {"nombre": "Dueño de Tienda A", "email": correo_a}
    assert b["dueno"]["email"] == correo_b
    assert [p["nombre"] for p in a["productos"]] == ["Grave", "Leve"]
    assert a["productos"][0] | {"id": None} == {
        "id": None,
        "nombre": "Grave",
        "sku": a["productos"][0]["sku"],
        "unidad": "unidad",
        "stock_actual": "2.000",
        "stock_minimo": "10.000",
        "deficit": "8.000",
    }
    assert [p["nombre"] for p in b["productos"]] == ["Arroz"]


async def test_sin_productos_bajo_minimo_devuelve_lista_vacia(cliente: httpx.AsyncClient) -> None:
    r = await cliente.get("/api/v1/integracion/stock-bajo")

    assert r.status_code == 200
    assert r.json() == {"negocios": []}
