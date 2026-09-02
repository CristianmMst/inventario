"""RF-CAT-007 y RNF-02: búsqueda por texto sobre nombre, SKU y categoría."""

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


async def _buscar(
    cliente: httpx.AsyncClient, auth: dict[str, str], q: str, **params: object
) -> dict:
    r = await cliente.get("/api/v1/productos/buscar", params={"q": q} | params, headers=auth)
    assert r.status_code == 200, r.text
    return r.json()


async def test_rf_cat_007_cuad_encuentra_cuaderno(cliente: httpx.AsyncClient) -> None:
    auth = await _auth(cliente)
    await _producto(cliente, auth, nombre="Cuaderno 100 hojas")
    await _producto(cliente, auth, nombre="Lápiz HB")
    nombres = [p["nombre"] for p in (await _buscar(cliente, auth, "cuad"))["datos"]]
    assert nombres == ["Cuaderno 100 hojas"]


async def test_rf_cat_007_papel_en_mayusculas_encuentra_papel(cliente: httpx.AsyncClient) -> None:
    auth = await _auth(cliente)
    await _producto(cliente, auth, nombre="papel bond carta")
    assert len((await _buscar(cliente, auth, "PAPEL"))["datos"]) == 1


async def test_rf_cat_007_la_busqueda_ignora_tildes_en_ambos_sentidos(
    cliente: httpx.AsyncClient,
) -> None:
    auth = await _auth(cliente)
    await _producto(cliente, auth, nombre="Lápiz HB")
    assert len((await _buscar(cliente, auth, "lapiz"))["datos"]) == 1
    assert len((await _buscar(cliente, auth, "LÁPIZ"))["datos"]) == 1


async def test_rf_cat_007_busca_tambien_por_sku(cliente: httpx.AsyncClient) -> None:
    auth = await _auth(cliente)
    await _producto(cliente, auth, nombre="Cuaderno", sku="CUAD-100")
    assert len((await _buscar(cliente, auth, "CUAD-1"))["datos"]) == 1


async def test_rf_cat_007_busca_tambien_por_categoria(cliente: httpx.AsyncClient) -> None:
    auth = await _auth(cliente)
    categoria = (
        await cliente.post("/api/v1/categorias", json={"nombre": "Papelería"}, headers=auth)
    ).json()
    await _producto(cliente, auth, nombre="Resma", categoria_id=categoria["id"])
    await _producto(cliente, auth, nombre="Grapadora")
    nombres = [p["nombre"] for p in (await _buscar(cliente, auth, "papeleria"))["datos"]]
    assert nombres == ["Resma"]


async def test_rf_cat_007_varias_palabras_exigen_todas(cliente: httpx.AsyncClient) -> None:
    auth = await _auth(cliente)
    await _producto(cliente, auth, nombre="Cuaderno rayado")
    await _producto(cliente, auth, nombre="Cuaderno cuadriculado")
    nombres = [p["nombre"] for p in (await _buscar(cliente, auth, "cuaderno ray"))["datos"]]
    assert nombres == ["Cuaderno rayado"]


async def test_rf_cat_007_los_resultados_se_paginan_por_cursor(cliente: httpx.AsyncClient) -> None:
    auth = await _auth(cliente)
    for i in range(5):
        await _producto(cliente, auth, nombre=f"Cuaderno {i}", sku=f"C-{i}")
    primera = await _buscar(cliente, auth, "cuaderno", limit=2)
    assert len(primera["datos"]) == 2 and primera["tiene_mas"]
    vistos = [p["id"] for p in primera["datos"]]
    pagina = primera
    while pagina["tiene_mas"]:
        pagina = await _buscar(
            cliente, auth, "cuaderno", limit=2, cursor=pagina["cursor_siguiente"]
        )
        vistos += [p["id"] for p in pagina["datos"]]
    assert len(vistos) == 5 and len(set(vistos)) == 5


async def test_rf_cat_007_no_devuelve_productos_de_otro_negocio(cliente: httpx.AsyncClient) -> None:
    a, b = await _auth(cliente), await _auth(cliente)
    await _producto(cliente, a, nombre="Cuaderno")
    assert (await _buscar(cliente, b, "cuaderno"))["datos"] == []


async def test_rf_cat_007_una_consulta_vacia_responde_422(cliente: httpx.AsyncClient) -> None:
    auth = await _auth(cliente)
    r = await cliente.get("/api/v1/productos/buscar", params={"q": "  "}, headers=auth)
    assert r.status_code == 422


async def test_rf_cat_007_los_caracteres_especiales_no_rompen_la_consulta(
    cliente: httpx.AsyncClient,
) -> None:
    auth = await _auth(cliente)
    await _producto(cliente, auth, nombre="Cuaderno")
    for raro in ["cuad:*", "a & b", "(", "'", "cuad|lapiz", "!x"]:
        r = await cliente.get("/api/v1/productos/buscar", params={"q": raro}, headers=auth)
        assert r.status_code == 200, (raro, r.text)
