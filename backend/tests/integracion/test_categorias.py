"""RF-CAT-005: categorías planas del negocio, con nombre único por negocio."""

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


async def test_rf_cat_005_alta_y_listado_de_categorias(cliente: httpx.AsyncClient) -> None:
    auth = await _auth(cliente)
    creada = await cliente.post("/api/v1/categorias", json={"nombre": "Cuadernos"}, headers=auth)
    assert creada.status_code == 201, creada.text
    assert creada.json()["nombre"] == "Cuadernos"
    listado = await cliente.get("/api/v1/categorias", headers=auth)
    assert listado.status_code == 200
    assert [c["nombre"] for c in listado.json()["datos"]] == ["Cuadernos"]


async def test_rf_cat_005_nombre_repetido_en_el_mismo_negocio_responde_409(
    cliente: httpx.AsyncClient,
) -> None:
    auth = await _auth(cliente)
    await cliente.post("/api/v1/categorias", json={"nombre": "Cuadernos"}, headers=auth)
    repetida = await cliente.post("/api/v1/categorias", json={"nombre": "cuadernos"}, headers=auth)
    assert repetida.status_code == 409
    assert repetida.json()["error"]["code"] == "CATEGORIA_DUPLICADA"


async def test_rf_cat_005_el_mismo_nombre_en_otro_negocio_se_acepta(
    cliente: httpx.AsyncClient,
) -> None:
    a, b = await _auth(cliente), await _auth(cliente)
    assert (
        await cliente.post("/api/v1/categorias", json={"nombre": "Cuadernos"}, headers=a)
    ).status_code == 201
    assert (
        await cliente.post("/api/v1/categorias", json={"nombre": "Cuadernos"}, headers=b)
    ).status_code == 201


async def test_rf_cat_005_editar_el_nombre(cliente: httpx.AsyncClient) -> None:
    auth = await _auth(cliente)
    creada = (
        await cliente.post("/api/v1/categorias", json={"nombre": "Cuadernos"}, headers=auth)
    ).json()
    editada = await cliente.patch(
        f"/api/v1/categorias/{creada['id']}", json={"nombre": "Libretas"}, headers=auth
    )
    assert editada.status_code == 200
    assert editada.json()["nombre"] == "Libretas"


async def test_rf_cat_005_editar_a_un_nombre_ya_usado_responde_409(
    cliente: httpx.AsyncClient,
) -> None:
    auth = await _auth(cliente)
    await cliente.post("/api/v1/categorias", json={"nombre": "Cuadernos"}, headers=auth)
    otra = (
        await cliente.post("/api/v1/categorias", json={"nombre": "Lápices"}, headers=auth)
    ).json()
    editada = await cliente.patch(
        f"/api/v1/categorias/{otra['id']}", json={"nombre": "Cuadernos"}, headers=auth
    )
    assert editada.status_code == 409


async def test_rf_aut_007_la_categoria_de_otro_negocio_no_existe(
    cliente: httpx.AsyncClient,
) -> None:
    a, b = await _auth(cliente), await _auth(cliente)
    creada = (
        await cliente.post("/api/v1/categorias", json={"nombre": "Cuadernos"}, headers=a)
    ).json()
    ajena = await cliente.patch(
        f"/api/v1/categorias/{creada['id']}", json={"nombre": "X"}, headers=b
    )
    assert ajena.status_code == 404
    assert ajena.json()["error"]["code"] == "CATEGORIA_NO_ENCONTRADA"
    assert (await cliente.get("/api/v1/categorias", headers=b)).json()["datos"] == []


async def test_rf_cat_005_el_nombre_es_obligatorio_y_se_recorta(cliente: httpx.AsyncClient) -> None:
    auth = await _auth(cliente)
    vacia = await cliente.post("/api/v1/categorias", json={"nombre": "   "}, headers=auth)
    assert vacia.status_code == 422
    con_espacios = await cliente.post(
        "/api/v1/categorias", json={"nombre": "  Papel  "}, headers=auth
    )
    assert con_espacios.json()["nombre"] == "Papel"
