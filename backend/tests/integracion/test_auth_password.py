"""RF-AUT-006: cambio de contraseña por el propio usuario, con revocación de refresh tokens."""

import httpx

from tests import fabricas

NUEVA = "Otra-contrasena-nueva-456"


async def _registrar(cliente: httpx.AsyncClient) -> dict:
    cuerpo = {
        "email": fabricas.correo_unico(),
        "password": fabricas.CONTRASENA_VALIDA,
        "nombre": "Marta",
        "negocio": {"nombre": "Papelería", "moneda_base": "COP", "zona_horaria": "America/Bogota"},
    }
    respuesta = await cliente.post("/api/v1/auth/registro", json=cuerpo)
    assert respuesta.status_code == 201
    return cuerpo | respuesta.json()


def _auth(sesion: dict) -> dict[str, str]:
    return {"Authorization": f"Bearer {sesion['token_acceso']}"}


async def test_rf_aut_006_cambiar_la_contrasena_invalida_la_anterior_y_acepta_la_nueva(
    cliente: httpx.AsyncClient,
) -> None:
    sesion = await _registrar(cliente)
    respuesta = await cliente.patch(
        "/api/v1/auth/password",
        json={"password_actual": sesion["password"], "password_nueva": NUEVA},
        headers=_auth(sesion),
    )
    assert respuesta.status_code == 204, respuesta.text

    vieja = await cliente.post(
        "/api/v1/auth/login", json={"email": sesion["email"], "password": sesion["password"]}
    )
    nueva = await cliente.post(
        "/api/v1/auth/login", json={"email": sesion["email"], "password": NUEVA}
    )
    assert vieja.status_code == 401
    assert nueva.status_code == 200


async def test_rf_aut_006_tras_cambiar_la_contrasena_el_refresh_anterior_deja_de_funcionar(
    cliente: httpx.AsyncClient,
) -> None:
    sesion = await _registrar(cliente)
    await cliente.patch(
        "/api/v1/auth/password",
        json={"password_actual": sesion["password"], "password_nueva": NUEVA},
        headers=_auth(sesion),
    )
    renovacion = await cliente.post(
        "/api/v1/auth/refresh", json={"token_renovacion": sesion["token_renovacion"]}
    )
    assert renovacion.status_code == 401


async def test_rf_aut_006_contrasena_actual_incorrecta_responde_422(
    cliente: httpx.AsyncClient,
) -> None:
    sesion = await _registrar(cliente)
    respuesta = await cliente.patch(
        "/api/v1/auth/password",
        json={"password_actual": "no-es-la-actual-1", "password_nueva": NUEVA},
        headers=_auth(sesion),
    )
    assert respuesta.status_code == 422
    assert respuesta.json()["error"]["code"] == "CONTRASENA_ACTUAL_INCORRECTA"


async def test_rf_aut_006_sin_credencial_responde_401(cliente: httpx.AsyncClient) -> None:
    respuesta = await cliente.patch(
        "/api/v1/auth/password", json={"password_actual": "x" * 8, "password_nueva": NUEVA}
    )
    assert respuesta.status_code == 401
    assert respuesta.json()["error"]["code"] == "CREDENCIAL_REQUERIDA"


async def test_rf_aut_006_un_token_de_acceso_corrupto_responde_401(
    cliente: httpx.AsyncClient,
) -> None:
    respuesta = await cliente.patch(
        "/api/v1/auth/password",
        json={"password_actual": "x" * 8, "password_nueva": NUEVA},
        headers={"Authorization": "Bearer no.es.jwt"},
    )
    assert respuesta.status_code == 401
    assert respuesta.json()["error"]["code"] == "CREDENCIAL_INVALIDA"
