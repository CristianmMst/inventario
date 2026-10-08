"""RF-AST-001..004: el asistente de inventario. Retell se reemplaza por un falso; la firma de las
herramientas se verifica de verdad."""

import json
import uuid
from collections.abc import AsyncIterator

import httpx
import pytest

from app.infra.retell import ClienteRetell, cliente_retell, firmar
from app.main import app
from tests import fabricas

CLAVE = "key_prueba"


class RetellFalso(ClienteRetell):
    def __init__(self) -> None:
        super().__init__(CLAVE, "agent_prueba", "https://retell.invalid")
        self.creados: list[dict[str, str]] = []
        self.recibidos: list[tuple[str, str]] = []
        self.terminados: list[str] = []

    async def crear_chat(self, metadata: dict[str, str]) -> str:
        self.creados.append(metadata)
        return f"chat_{uuid.uuid4().hex}"

    async def completar(self, chat_id: str, contenido: str) -> list[str]:
        self.recibidos.append((chat_id, contenido))
        return ["Hay 2 productos bajo el mínimo."]

    async def terminar_chat(self, chat_id: str) -> None:
        self.terminados.append(chat_id)


@pytest.fixture
async def retell() -> AsyncIterator[RetellFalso]:
    falso = RetellFalso()
    app.dependency_overrides[cliente_retell] = lambda: falso
    yield falso
    app.dependency_overrides.pop(cliente_retell, None)


async def _negocio(cliente: httpx.AsyncClient, nombre: str) -> dict:
    r = await cliente.post(
        "/api/v1/auth/registro",
        json={
            "email": fabricas.correo_unico(),
            "password": fabricas.CONTRASENA_VALIDA,
            "nombre": f"Dueño de {nombre}",
            "negocio": {"nombre": nombre, "moneda_base": "COP", "zona_horaria": "UTC"},
        },
    )
    assert r.status_code == 201, r.text
    return {"Authorization": f"Bearer {r.json()['token_acceso']}"}


async def _producto(
    cliente: httpx.AsyncClient, auth: dict, nombre: str, minimo: str, stock: str
) -> None:
    r = await cliente.post(
        "/api/v1/productos",
        json={"nombre": nombre, "unidad_codigo": "unidad", "stock_minimo": minimo},
        headers=auth,
    )
    assert r.status_code == 201, r.text
    if stock == "0":
        return
    r = await cliente.post(
        "/api/v1/movimientos",
        json={
            "producto_id": r.json()["id"],
            "tipo": "entrada",
            "cantidad": stock,
            "motivo": "carga_inicial",
        },
        headers=auth | {"Idempotency-Key": str(uuid.uuid4())},
    )
    assert r.status_code == 201, r.text


async def _chat(cliente: httpx.AsyncClient, auth: dict) -> str:
    r = await cliente.post("/api/v1/asistente/chats", headers=auth)
    assert r.status_code == 201, r.text
    return r.json()["chat_id"]


async def _herramienta(
    cliente: httpx.AsyncClient,
    nombre: str,
    chat_id: str,
    args: dict | None = None,
    clave: str = CLAVE,
) -> httpx.Response:
    cuerpo = json.dumps({"name": nombre, "args": args or {}, "chat": {"chat_id": chat_id}})
    return await cliente.post(
        f"/api/v1/asistente/herramientas/{nombre}",
        content=cuerpo,
        headers={"Content-Type": "application/json", "X-Retell-Signature": firmar(cuerpo, clave)},
    )


async def test_rf_ast_001_002_crea_chat_y_conversa(
    cliente: httpx.AsyncClient, retell: RetellFalso
) -> None:
    auth = await _negocio(cliente, "Tienda A")
    chat_id = await _chat(cliente, auth)

    r = await cliente.post(
        f"/api/v1/asistente/chats/{chat_id}/mensajes",
        json={"contenido": "  ¿Qué está por agotarse?  "},
        headers=auth,
    )

    assert r.status_code == 200, r.text
    assert r.json() == {"mensajes": ["Hay 2 productos bajo el mínimo."]}
    assert retell.recibidos == [(chat_id, "¿Qué está por agotarse?")]
    assert set(retell.creados[0]) == {"negocio_id"}


async def test_rf_ast_002_un_negocio_no_usa_el_chat_de_otro(
    cliente: httpx.AsyncClient, retell: RetellFalso
) -> None:
    chat_a = await _chat(cliente, await _negocio(cliente, "Tienda A"))
    auth_b = await _negocio(cliente, "Tienda B")

    r = await cliente.post(
        f"/api/v1/asistente/chats/{chat_a}/mensajes", json={"contenido": "hola"}, headers=auth_b
    )

    assert r.status_code == 404
    assert r.json()["error"]["code"] == "CHAT_NO_ENCONTRADO"
    assert retell.recibidos == []


async def test_rf_ast_001_terminar_es_idempotente_y_cierra_el_chat(
    cliente: httpx.AsyncClient, retell: RetellFalso
) -> None:
    auth = await _negocio(cliente, "Tienda A")
    chat_id = await _chat(cliente, auth)
    ruta = f"/api/v1/asistente/chats/{chat_id}"

    assert (await cliente.delete(ruta, headers=auth)).status_code == 204
    assert (await cliente.delete(ruta, headers=auth)).status_code == 204
    r = await cliente.post(f"{ruta}/mensajes", json={"contenido": "hola"}, headers=auth)

    assert retell.terminados == [chat_id]
    assert r.status_code == 409
    assert (await _herramienta(cliente, "stock-bajo", chat_id)).status_code == 404


async def test_rf_ast_001_sin_retell_configurado_responde_503(cliente: httpx.AsyncClient) -> None:
    app.dependency_overrides[cliente_retell] = lambda: ClienteRetell("", "", "https://x.invalid")
    try:
        r = await cliente.post("/api/v1/asistente/chats", headers=await _negocio(cliente, "T"))
    finally:
        app.dependency_overrides.pop(cliente_retell, None)
    assert r.status_code == 503
    assert r.json()["error"]["code"] == "ASISTENTE_NO_CONFIGURADO"


async def test_rf_ast_003_004_herramientas_responden_con_el_negocio_del_chat(
    cliente: httpx.AsyncClient, retell: RetellFalso
) -> None:
    auth_a = await _negocio(cliente, "Tienda A")
    await _producto(cliente, auth_a, "Arroz Diana", "10", "2")
    await _producto(cliente, auth_a, "Azúcar", "5", "0")
    await _producto(cliente, auth_a, "Aceite", "5", "40")
    auth_b = await _negocio(cliente, "Tienda B")
    await _producto(cliente, auth_b, "Arroz Roa", "10", "1")
    chat_a = await _chat(cliente, auth_a)

    bajo = await _herramienta(cliente, "stock-bajo", chat_a)
    agotados = await _herramienta(cliente, "agotados", chat_a)
    busqueda = await _herramienta(cliente, "buscar-producto", chat_a, {"texto": "arroz"})

    assert bajo.status_code == 200, bajo.text
    assert [p["nombre"] for p in bajo.json()["productos"]] == ["Azúcar", "Arroz Diana"]
    assert [p["nombre"] for p in agotados.json()["productos"]] == ["Azúcar"]
    assert agotados.json()["productos"][0]["estado_stock"] == "agotado"
    assert busqueda.status_code == 200, busqueda.text
    assert [p["nombre"] for p in busqueda.json()["productos"]] == ["Arroz Diana"]
    assert busqueda.json()["productos"][0] | {"sku": None} == {
        "nombre": "Arroz Diana",
        "sku": None,
        "unidad": "unidad",
        "stock_actual": "2.000",
        "stock_minimo": "10.000",
        "estado_stock": "bajo_minimo",
    }


async def test_rf_ast_004_sin_firma_valida_no_hay_datos(
    cliente: httpx.AsyncClient, retell: RetellFalso
) -> None:
    chat_id = await _chat(cliente, await _negocio(cliente, "Tienda A"))

    otra_clave = await _herramienta(cliente, "stock-bajo", chat_id, clave="key_ajena")
    sin_firma = await cliente.post(
        "/api/v1/asistente/herramientas/stock-bajo",
        json={"args": {}, "chat": {"chat_id": chat_id}},
    )
    chat_inventado = await _herramienta(cliente, "stock-bajo", "chat_que_no_existe")

    assert otra_clave.status_code == 401
    assert otra_clave.json()["error"]["code"] == "FIRMA_INVALIDA"
    assert sin_firma.status_code == 401
    assert chat_inventado.status_code == 404
