"""RF-AUT-007 y RNF-12: contexto_actual() resuelve JWT y X-API-Key; aislamiento con 404."""

import uuid

import httpx
import pytest
import sqlalchemy as sa
from sqlalchemy.ext.asyncio import AsyncSession

from tests import fabricas


async def _negocio(cliente: httpx.AsyncClient, nombre: str) -> dict:
    cuerpo = {
        "email": fabricas.correo_unico(),
        "password": fabricas.CONTRASENA_VALIDA,
        "nombre": "Dueño",
        "negocio": {"nombre": nombre, "moneda_base": "COP", "zona_horaria": "America/Bogota"},
    }
    sesion = (await cliente.post("/api/v1/auth/registro", json=cuerpo)).json()
    jwt_ = {"Authorization": f"Bearer {sesion['token_acceso']}"}
    creada = (await cliente.post("/api/v1/api-keys", json={"nombre": "svc"}, headers=jwt_)).json()
    return {
        "id": sesion["negocio"]["id"],
        "jwt": jwt_,
        "api_key": {"X-API-Key": creada["clave"]},
        "api_key_id": creada["id"],
    }


async def test_rf_aut_007_get_negocio_devuelve_el_negocio_de_la_credencial_con_jwt_y_api_key(
    cliente: httpx.AsyncClient,
) -> None:
    n = await _negocio(cliente, "Papelería Marta")
    con_jwt = await cliente.get("/api/v1/negocio", headers=n["jwt"])
    con_key = await cliente.get("/api/v1/negocio", headers=n["api_key"])
    assert con_jwt.status_code == 200 and con_key.status_code == 200
    assert con_jwt.json()["id"] == n["id"] == con_key.json()["id"]
    assert con_jwt.json()["nombre"] == "Papelería Marta"


@pytest.mark.parametrize("credencial", ["jwt", "api_key"])
async def test_rf_aut_007_un_recurso_de_otro_negocio_responde_404_nunca_403(
    cliente: httpx.AsyncClient, credencial: str
) -> None:
    a = await _negocio(cliente, "A")
    b = await _negocio(cliente, "B")
    respuesta = await cliente.delete(f"/api/v1/api-keys/{a['api_key_id']}", headers=b[credencial])
    assert respuesta.status_code == 404
    assert respuesta.json()["error"]["code"] == "API_KEY_NO_ENCONTRADA"
    # Y el recurso de A sigue intacto.
    listado = (await cliente.get("/api/v1/api-keys", headers=a["jwt"])).json()["datos"]
    assert listado[0]["revocado_en"] is None


async def test_rf_aut_007_el_listado_solo_muestra_lo_del_propio_negocio(
    cliente: httpx.AsyncClient,
) -> None:
    a = await _negocio(cliente, "A")
    b = await _negocio(cliente, "B")
    ids_b = {
        f["id"] for f in (await cliente.get("/api/v1/api-keys", headers=b["jwt"])).json()["datos"]
    }
    assert a["api_key_id"] not in ids_b and b["api_key_id"] in ids_b


async def test_rf_aut_005_usar_una_api_key_registra_su_ultimo_uso(
    cliente: httpx.AsyncClient, sesion: AsyncSession
) -> None:
    n = await _negocio(cliente, "A")
    await cliente.get("/api/v1/negocio", headers=n["api_key"])
    ultimo = (
        await sesion.execute(
            sa.text("select ultimo_uso_en from api_keys where id = :id"), {"id": n["api_key_id"]}
        )
    ).scalar_one()
    assert ultimo is not None


async def test_rf_aut_005_una_api_key_revocada_responde_401(cliente: httpx.AsyncClient) -> None:
    n = await _negocio(cliente, "A")
    await cliente.delete(f"/api/v1/api-keys/{n['api_key_id']}", headers=n["jwt"])
    respuesta = await cliente.get("/api/v1/negocio", headers=n["api_key"])
    assert respuesta.status_code == 401
    assert respuesta.json()["error"]["code"] == "CREDENCIAL_INVALIDA"


@pytest.mark.parametrize(
    "clave", ["inv_00000000_secretoinventado", "sin-formato", f"inv_{uuid.uuid4().hex[:8]}_x"]
)
async def test_rnf_12_una_api_key_invalida_responde_401_sin_detalles(
    cliente: httpx.AsyncClient, clave: str
) -> None:
    respuesta = await cliente.get("/api/v1/negocio", headers={"X-API-Key": clave})
    assert respuesta.status_code == 401
    assert respuesta.json()["error"]["details"] == {}


async def test_rf_aut_007_el_autor_de_una_api_key_es_de_tipo_servicio(
    cliente: httpx.AsyncClient,
) -> None:
    n = await _negocio(cliente, "A")
    con_key = (await cliente.get("/api/v1/negocio", headers=n["api_key"])).json()
    con_jwt = (await cliente.get("/api/v1/negocio", headers=n["jwt"])).json()
    assert con_key["credencial"]["tipo"] == "servicio"
    assert con_jwt["credencial"]["tipo"] == "usuario"
