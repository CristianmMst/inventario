"""RF-CAT-014: listado de productos paginado con filtros por categoría y estado.

El filtro por condición de stock se añadió al cerrar H3, con `stock_productos` ya creada.
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


async def _listar(cliente: httpx.AsyncClient, auth: dict[str, str], **params: object) -> dict:
    r = await cliente.get("/api/v1/productos", params=params, headers=auth)
    assert r.status_code == 200, r.text
    return r.json()


async def test_rf_cat_014_el_listado_va_ordenado_por_nombre_y_paginado_por_cursor(
    cliente: httpx.AsyncClient,
) -> None:
    auth = await _auth(cliente)
    for nombre in ["Lápiz", "Cuaderno", "Borrador", "Regla", "Tijeras"]:
        await _producto(cliente, auth, nombre=nombre)
    primera = await _listar(cliente, auth, limit=2)
    assert [p["nombre"] for p in primera["datos"]] == ["Borrador", "Cuaderno"]
    assert primera["tiene_mas"]
    nombres = [p["nombre"] for p in primera["datos"]]
    pagina = primera
    while pagina["tiene_mas"]:
        pagina = await _listar(cliente, auth, limit=2, cursor=pagina["cursor_siguiente"])
        nombres += [p["nombre"] for p in pagina["datos"]]
    assert nombres == ["Borrador", "Cuaderno", "Lápiz", "Regla", "Tijeras"]


async def test_rf_cat_014_filtro_por_categoria(cliente: httpx.AsyncClient) -> None:
    auth = await _auth(cliente)
    c1 = (await cliente.post("/api/v1/categorias", json={"nombre": "A"}, headers=auth)).json()
    c2 = (await cliente.post("/api/v1/categorias", json={"nombre": "B"}, headers=auth)).json()
    await _producto(cliente, auth, nombre="En A", categoria_id=c1["id"])
    await _producto(cliente, auth, nombre="En B", categoria_id=c2["id"])
    await _producto(cliente, auth, nombre="Sin categoría")
    nombres = [p["nombre"] for p in (await _listar(cliente, auth, categoria_id=c1["id"]))["datos"]]
    assert nombres == ["En A"]


async def test_rf_cat_014_por_defecto_solo_activos_y_filtro_por_estado(
    cliente: httpx.AsyncClient,
) -> None:
    auth = await _auth(cliente)
    activo = await _producto(cliente, auth, nombre="Activo")
    archivado = await _producto(cliente, auth, nombre="Archivado")
    r = await cliente.post(f"/api/v1/productos/{archivado['id']}/archivar", headers=auth)
    assert r.status_code == 200, r.text

    por_defecto = [p["id"] for p in (await _listar(cliente, auth))["datos"]]
    assert por_defecto == [activo["id"]]
    archivados = [p["id"] for p in (await _listar(cliente, auth, estado="archivado"))["datos"]]
    assert archivados == [archivado["id"]]
    todos = [p["id"] for p in (await _listar(cliente, auth, estado="todos"))["datos"]]
    assert set(todos) == {activo["id"], archivado["id"]}


async def test_rf_cat_014_filtros_combinados(cliente: httpx.AsyncClient) -> None:
    auth = await _auth(cliente)
    c1 = (await cliente.post("/api/v1/categorias", json={"nombre": "A"}, headers=auth)).json()
    a_activo = await _producto(cliente, auth, nombre="A activo", categoria_id=c1["id"])
    a_arch = await _producto(cliente, auth, nombre="A archivado", categoria_id=c1["id"])
    await _producto(cliente, auth, nombre="Otro archivado")
    await cliente.post(f"/api/v1/productos/{a_arch['id']}/archivar", headers=auth)
    ids = [
        p["id"]
        for p in (await _listar(cliente, auth, categoria_id=c1["id"], estado="archivado"))["datos"]
    ]
    assert ids == [a_arch["id"]]
    assert a_activo["id"] not in ids


async def test_rf_cat_014_un_estado_desconocido_responde_422(cliente: httpx.AsyncClient) -> None:
    auth = await _auth(cliente)
    r = await cliente.get("/api/v1/productos", params={"estado": "borrado"}, headers=auth)
    assert r.status_code == 422


async def test_rf_aut_007_el_listado_no_mezcla_negocios(cliente: httpx.AsyncClient) -> None:
    a, b = await _auth(cliente), await _auth(cliente)
    await _producto(cliente, a, nombre="De A")
    assert (await _listar(cliente, b))["datos"] == []


async def _cargar(
    cliente: httpx.AsyncClient, auth: dict[str, str], producto_id: str, cantidad: str
) -> None:
    import uuid

    r = await cliente.post(
        "/api/v1/movimientos",
        json={
            "producto_id": producto_id,
            "tipo": "entrada",
            "cantidad": cantidad,
            "motivo": "carga_inicial",
        },
        headers=auth | {"Idempotency-Key": str(uuid.uuid4())},
    )
    assert r.status_code == 201, r.text


async def test_rf_cat_014_filtro_por_condicion_de_stock(cliente: httpx.AsyncClient) -> None:
    auth = await _auth(cliente)
    agotado = await _producto(cliente, auth, nombre="Agotado", stock_minimo="2")
    bajo = await _producto(cliente, auth, nombre="Bajo mínimo", stock_minimo="5")
    con = await _producto(cliente, auth, nombre="Con stock", stock_minimo="1")
    sin_minimo = await _producto(cliente, auth, nombre="Sin mínimo")
    await _cargar(cliente, auth, bajo["id"], "3")
    await _cargar(cliente, auth, con["id"], "10")
    await _cargar(cliente, auth, sin_minimo["id"], "1")

    def nombres(datos: dict) -> set[str]:
        return {p["nombre"] for p in datos["datos"]}

    assert nombres(await _listar(cliente, auth, condicion_stock="agotado")) == {"Agotado"}
    assert nombres(await _listar(cliente, auth, condicion_stock="bajo_minimo")) == {
        "Agotado",
        "Bajo mínimo",
    }
    assert nombres(await _listar(cliente, auth, condicion_stock="con_stock")) == {
        "Bajo mínimo",
        "Con stock",
        "Sin mínimo",
    }
    r = await cliente.get("/api/v1/productos", params={"condicion_stock": "mucho"}, headers=auth)
    assert r.status_code == 422


async def test_rf_cat_014_los_tres_filtros_combinados(cliente: httpx.AsyncClient) -> None:
    auth = await _auth(cliente)
    c1 = (await cliente.post("/api/v1/categorias", json={"nombre": "A"}, headers=auth)).json()
    objetivo = await _producto(
        cliente, auth, nombre="Objetivo", categoria_id=c1["id"], stock_minimo="5"
    )
    otro_bajo = await _producto(cliente, auth, nombre="Otro bajo", stock_minimo="5")
    archivado = await _producto(
        cliente, auth, nombre="Archivado", categoria_id=c1["id"], stock_minimo="5"
    )
    await _cargar(cliente, auth, objetivo["id"], "1")
    await _cargar(cliente, auth, otro_bajo["id"], "1")
    await cliente.post(f"/api/v1/productos/{archivado['id']}/archivar", headers=auth)
    datos = await _listar(
        cliente, auth, categoria_id=c1["id"], estado="activo", condicion_stock="bajo_minimo"
    )
    assert [p["nombre"] for p in datos["datos"]] == ["Objetivo"]
