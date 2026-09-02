"""RF-INT-005, RF-INT-006, RF-INT-007: contrato de webhooks salientes. Se persisten las
suscripciones y se documenta el contrato; en v1 no se dispara ninguna petición saliente."""

import re
from pathlib import Path

import httpx
import sqlalchemy as sa
from sqlalchemy.ext.asyncio import AsyncSession

from tests import fabricas

RAIZ = Path(__file__).resolve().parents[2]


async def _sesion(cliente: httpx.AsyncClient) -> dict:
    cuerpo = {
        "email": fabricas.correo_unico(),
        "password": fabricas.CONTRASENA_VALIDA,
        "nombre": "Marta",
        "negocio": {"nombre": "P", "moneda_base": "COP", "zona_horaria": "UTC"},
    }
    r = await cliente.post("/api/v1/auth/registro", json=cuerpo)
    return {"Authorization": f"Bearer {r.json()['token_acceso']}"}


async def _suscribir(cliente: httpx.AsyncClient, auth: dict, **extra: object) -> httpx.Response:
    cuerpo = {
        "url": "https://n8n.ejemplo.com/webhook/inventario",
        "tipos": ["stock.bajo_minimo", "compra.recibida"],
        "secreto": "un-secreto-largo-de-al-menos-32-caracteres!!",
        "descripcion": "Pedido automático al proveedor",
    } | extra
    return await cliente.post("/api/v1/webhooks", json=cuerpo, headers=auth)


async def test_rf_int_005_alta_de_suscripcion_persiste_con_el_secreto_en_hash(
    cliente: httpx.AsyncClient, sesion: AsyncSession
) -> None:
    auth = await _sesion(cliente)
    r = await _suscribir(cliente, auth)
    assert r.status_code == 201, r.text
    s = r.json()
    assert s["url"].startswith("https://") and s["tipos"] == [
        "compra.recibida",
        "stock.bajo_minimo",
    ]
    assert s["activa"] is True and s["descripcion"] == "Pedido automático al proveedor"
    assert "secreto" not in s and "secreto_hash" not in s
    fila = (
        await sesion.execute(
            sa.text("select secreto_hash from suscripciones_webhook where id = :id"),
            {"id": s["id"]},
        )
    ).scalar_one()
    assert fila.startswith("$argon2id$")
    assert "un-secreto-largo" not in fila


async def test_rf_int_005_listado_y_baja(cliente: httpx.AsyncClient) -> None:
    auth = await _sesion(cliente)
    a = (await _suscribir(cliente, auth)).json()
    b = (await _suscribir(cliente, auth, url="https://otro.ejemplo.com/hook", tipos=["*"])).json()
    listado = await cliente.get("/api/v1/webhooks", headers=auth)
    assert listado.status_code == 200
    assert {x["id"] for x in listado.json()["datos"]} == {a["id"], b["id"]}
    baja = await cliente.delete(f"/api/v1/webhooks/{a['id']}", headers=auth)
    assert baja.status_code == 204
    assert [
        x["id"] for x in (await cliente.get("/api/v1/webhooks", headers=auth)).json()["datos"]
    ] == [b["id"]]
    assert (await cliente.delete(f"/api/v1/webhooks/{a['id']}", headers=auth)).status_code == 404


async def test_rf_int_005_los_tipos_deben_ser_del_catalogo_o_el_comodin(
    cliente: httpx.AsyncClient,
) -> None:
    auth = await _sesion(cliente)
    r = await _suscribir(cliente, auth, tipos=["stock.bajo_minimo", "evento.inventado"])
    assert r.status_code == 422
    assert r.json()["error"]["code"] == "TIPO_DE_EVENTO_DESCONOCIDO"
    assert r.json()["error"]["details"]["tipos_desconocidos"] == ["evento.inventado"]
    comodin = await _suscribir(cliente, auth, tipos=["*"])
    assert comodin.status_code == 201 and comodin.json()["tipos"] == ["*"]
    vacio = await _suscribir(cliente, auth, tipos=[])
    assert vacio.status_code == 422


async def test_rnf_11_la_url_debe_ser_https_y_el_secreto_largo(cliente: httpx.AsyncClient) -> None:
    auth = await _sesion(cliente)
    assert (
        await _suscribir(cliente, auth, url="http://inseguro.ejemplo.com/hook")
    ).status_code == 422
    assert (await _suscribir(cliente, auth, secreto="corto")).status_code == 422


async def _contrato(cliente: httpx.AsyncClient) -> str:
    openapi = (await cliente.get("/openapi.json")).json()
    ruta = openapi["paths"]["/api/v1/webhooks"]
    return (ruta["post"].get("description") or "") + (ruta["post"].get("summary") or "")


async def test_rf_int_006_la_firma_hmac_con_momento_figura_en_el_contrato(
    cliente: httpx.AsyncClient,
) -> None:
    texto = await _contrato(cliente)
    assert "HMAC" in texto and "X-Evento-Firma" in texto and "X-Evento-Momento" in texto
    assert "no se implementa" in texto.lower()


async def test_rf_int_007_la_entrega_al_menos_una_vez_con_reintentos_figura_en_el_contrato(
    cliente: httpx.AsyncClient,
) -> None:
    texto = await _contrato(cliente)
    assert "al menos una vez" in texto and "X-Evento-Id" in texto
    assert "reintenta" in texto and "espera creciente" in texto


def test_rf_int_005_ninguna_peticion_saliente_en_el_codigo() -> None:
    """En v1 no hay entrega: el backend no hace peticiones HTTP salientes a las suscripciones."""
    sospechosos = re.compile(
        r"\b(httpx\.(AsyncClient|post|get)|aiohttp|requests\.(post|get)|urlopen)\b"
    )
    culpables = [
        str(a.relative_to(RAIZ))
        for a in (RAIZ / "app").rglob("*.py")
        if sospechosos.search(a.read_text("utf-8"))
    ]
    assert culpables == []


async def test_rf_aut_007_las_suscripciones_no_cruzan_negocios(cliente: httpx.AsyncClient) -> None:
    a, b = await _sesion(cliente), await _sesion(cliente)
    s = (await _suscribir(cliente, a)).json()
    assert (await cliente.get("/api/v1/webhooks", headers=b)).json()["datos"] == []
    assert (await cliente.delete(f"/api/v1/webhooks/{s['id']}", headers=b)).status_code == 404
