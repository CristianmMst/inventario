"""RF-AUT-003 y RNF-11: renovación con rotación y detección de reúso."""

import httpx
import sqlalchemy as sa
from sqlalchemy.ext.asyncio import AsyncSession

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
    return respuesta.json()


async def _renovar(cliente: httpx.AsyncClient, token: str) -> httpx.Response:
    return await cliente.post("/api/v1/auth/refresh", json={"token_renovacion": token})


async def test_rf_aut_003_renovar_devuelve_tokens_nuevos_y_revoca_el_anterior(
    cliente: httpx.AsyncClient,
) -> None:
    sesion = await _registrar(cliente)
    renovada = await _renovar(cliente, sesion["token_renovacion"])
    assert renovada.status_code == 200, renovada.text
    nueva = renovada.json()
    assert nueva["token_acceso"] and nueva["token_renovacion"]
    assert nueva["token_renovacion"] != sesion["token_renovacion"]

    otra_vez = await _renovar(cliente, sesion["token_renovacion"])
    assert otra_vez.status_code == 401
    assert otra_vez.json()["error"]["code"] == "CREDENCIAL_INVALIDA"


async def test_rnf_11_reusar_un_token_revocado_revoca_toda_la_familia(
    cliente: httpx.AsyncClient,
) -> None:
    sesion = await _registrar(cliente)
    a = sesion["token_renovacion"]
    b = (await _renovar(cliente, a)).json()["token_renovacion"]
    c = (await _renovar(cliente, b)).json()["token_renovacion"]

    # Alguien copió `a` y lo intenta usar: señal de robo.
    assert (await _renovar(cliente, a)).status_code == 401
    # El token legítimo vigente `c` también queda revocado.
    assert (await _renovar(cliente, c)).status_code == 401


async def test_rnf_11_el_token_de_renovacion_es_opaco_y_solo_se_guarda_su_hash(
    cliente: httpx.AsyncClient, sesion: AsyncSession
) -> None:
    datos = await _registrar(cliente)
    token = datos["token_renovacion"]
    assert "." not in token, "un refresh token nunca es un JWT"
    assert len(token) >= 43, "al menos 256 bits en base64url"
    guardados = (await sesion.execute(sa.text("select token_hash from refresh_tokens"))).scalars()
    assert all(h != token for h in guardados)


async def test_rf_aut_003_un_token_caducado_no_renueva(
    cliente: httpx.AsyncClient, sesion: AsyncSession
) -> None:
    datos = await _registrar(cliente)
    await sesion.execute(sa.text("update refresh_tokens set expira_en = now() - interval '1 day'"))
    await sesion.commit()
    assert (await _renovar(cliente, datos["token_renovacion"])).status_code == 401


async def test_rf_aut_003_la_vida_del_token_de_renovacion_es_de_60_dias(
    cliente: httpx.AsyncClient, sesion: AsyncSession
) -> None:
    await _registrar(cliente)
    dias = (
        await sesion.execute(
            sa.text("select extract(day from expira_en - created_at) from refresh_tokens")
        )
    ).scalar_one()
    assert 59 <= dias <= 60


async def test_rf_aut_002_logout_revoca_el_token_de_renovacion(cliente: httpx.AsyncClient) -> None:
    datos = await _registrar(cliente)
    salida = await cliente.post(
        "/api/v1/auth/logout", json={"token_renovacion": datos["token_renovacion"]}
    )
    assert salida.status_code == 204
    assert (await _renovar(cliente, datos["token_renovacion"])).status_code == 401


async def test_rf_aut_003_un_token_inventado_responde_401(cliente: httpx.AsyncClient) -> None:
    assert (await _renovar(cliente, "x" * 43)).status_code == 401
