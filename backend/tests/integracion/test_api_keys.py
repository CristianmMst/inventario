"""RF-AUT-005: credenciales de servicio; el secreto se muestra una sola vez."""

import httpx
import sqlalchemy as sa
from sqlalchemy.ext.asyncio import AsyncSession

from tests import fabricas


async def _sesion(cliente: httpx.AsyncClient) -> dict:
    cuerpo = {
        "email": fabricas.correo_unico(),
        "password": fabricas.CONTRASENA_VALIDA,
        "nombre": "Marta",
        "negocio": {"nombre": "Papelería", "moneda_base": "COP", "zona_horaria": "America/Bogota"},
    }
    respuesta = await cliente.post("/api/v1/auth/registro", json=cuerpo)
    assert respuesta.status_code == 201
    return {"headers": {"Authorization": f"Bearer {respuesta.json()['token_acceso']}"}}


async def test_rf_aut_005_crear_devuelve_el_secreto_completo_una_sola_vez(
    cliente: httpx.AsyncClient, sesion: AsyncSession
) -> None:
    s = await _sesion(cliente)
    creada = await cliente.post("/api/v1/api-keys", json={"nombre": "n8n"}, headers=s["headers"])
    assert creada.status_code == 201, creada.text
    datos = creada.json()
    assert datos["nombre"] == "n8n"
    assert datos["clave"].startswith("inv_")
    prefijo = datos["clave"].split("_")[1]
    assert datos["prefijo"] == prefijo and len(prefijo) == 8

    listado = await cliente.get("/api/v1/api-keys", headers=s["headers"])
    assert listado.status_code == 200
    fila = listado.json()["datos"][0]
    assert fila["prefijo"] == prefijo
    assert "clave" not in fila and "secreto" not in fila
    assert datos["clave"] not in listado.text

    fila_bd = (await sesion.execute(sa.text("select prefijo, secreto_hash from api_keys"))).one()
    assert fila_bd.prefijo == prefijo
    assert fila_bd.secreto_hash.startswith("$argon2id$")
    assert datos["clave"].split("_")[2] not in fila_bd.secreto_hash


async def test_rf_aut_005_el_listado_muestra_nombre_creacion_ultimo_uso_y_revocacion(
    cliente: httpx.AsyncClient,
) -> None:
    s = await _sesion(cliente)
    await cliente.post("/api/v1/api-keys", json={"nombre": "n8n"}, headers=s["headers"])
    fila = (await cliente.get("/api/v1/api-keys", headers=s["headers"])).json()["datos"][0]
    assert set(fila) >= {"id", "nombre", "prefijo", "created_at", "ultimo_uso_en", "revocado_en"}
    assert fila["ultimo_uso_en"] is None and fila["revocado_en"] is None


async def test_rf_aut_005_revocar_marca_la_clave_y_la_deja_visible_en_el_listado(
    cliente: httpx.AsyncClient,
) -> None:
    s = await _sesion(cliente)
    creada = (
        await cliente.post("/api/v1/api-keys", json={"nombre": "n8n"}, headers=s["headers"])
    ).json()
    borrada = await cliente.delete(f"/api/v1/api-keys/{creada['id']}", headers=s["headers"])
    assert borrada.status_code == 204
    fila = (await cliente.get("/api/v1/api-keys", headers=s["headers"])).json()["datos"][0]
    assert fila["revocado_en"] is not None


async def test_rf_aut_005_revocar_una_clave_inexistente_responde_404(
    cliente: httpx.AsyncClient,
) -> None:
    s = await _sesion(cliente)
    import uuid

    respuesta = await cliente.delete(f"/api/v1/api-keys/{uuid.uuid4()}", headers=s["headers"])
    assert respuesta.status_code == 404
    assert respuesta.json()["error"]["code"] == "API_KEY_NO_ENCONTRADA"


async def test_rf_aut_005_sin_sesion_no_se_pueden_emitir_claves(cliente: httpx.AsyncClient) -> None:
    respuesta = await cliente.post("/api/v1/api-keys", json={"nombre": "n8n"})
    assert respuesta.status_code == 401
