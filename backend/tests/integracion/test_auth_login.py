"""RF-AUT-002 y RNF-11: inicio de sesión con JWT de acceso de vida corta."""

import httpx
import jwt

from app.config import obtener_ajustes
from tests import fabricas


async def _registrar(cliente: httpx.AsyncClient) -> dict:
    cuerpo = {
        "email": fabricas.correo_unico(),
        "password": fabricas.CONTRASENA_VALIDA,
        "nombre": "Marta",
        "negocio": {"nombre": "Papelería", "moneda_base": "COP", "zona_horaria": "America/Bogota"},
    }
    respuesta = await cliente.post("/api/v1/auth/registro", json=cuerpo)
    assert respuesta.status_code == 201
    return cuerpo | {"respuesta": respuesta.json()}


async def test_rf_aut_002_credencial_correcta_devuelve_token_valido_de_15_minutos(
    cliente: httpx.AsyncClient,
) -> None:
    registro = await _registrar(cliente)
    respuesta = await cliente.post(
        "/api/v1/auth/login", json={"email": registro["email"], "password": registro["password"]}
    )
    assert respuesta.status_code == 200, respuesta.text
    datos = respuesta.json()
    carga = jwt.decode(datos["token_acceso"], obtener_ajustes().jwt_secreto, algorithms=["HS256"])
    assert carga["sub"] == registro["respuesta"]["usuario"]["id"]
    assert carga["biz"] == registro["respuesta"]["negocio"]["id"]
    assert carga["exp"] - carga["iat"] == 15 * 60
    assert datos["expira_en_segundos"] == 15 * 60


async def test_rf_aut_002_contrasena_incorrecta_responde_401_credencial_invalida(
    cliente: httpx.AsyncClient,
) -> None:
    registro = await _registrar(cliente)
    respuesta = await cliente.post(
        "/api/v1/auth/login", json={"email": registro["email"], "password": "otra-contrasena-1"}
    )
    assert respuesta.status_code == 401
    assert respuesta.json()["error"]["code"] == "CREDENCIAL_INVALIDA"


async def test_rnf_11_correo_inexistente_responde_igual_que_contrasena_incorrecta(
    cliente: httpx.AsyncClient,
) -> None:
    respuesta = await cliente.post(
        "/api/v1/auth/login",
        json={"email": fabricas.correo_unico(), "password": fabricas.CONTRASENA_VALIDA},
    )
    assert respuesta.status_code == 401
    assert respuesta.json()["error"]["code"] == "CREDENCIAL_INVALIDA"
    assert respuesta.json()["error"]["details"] == {}


async def test_rf_aut_002_el_correo_del_login_no_distingue_mayusculas(
    cliente: httpx.AsyncClient,
) -> None:
    registro = await _registrar(cliente)
    respuesta = await cliente.post(
        "/api/v1/auth/login",
        json={"email": registro["email"].upper(), "password": registro["password"]},
    )
    assert respuesta.status_code == 200
