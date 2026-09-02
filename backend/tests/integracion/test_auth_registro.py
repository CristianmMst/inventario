"""RF-AUT-001 y RF-AUT-004: registro self-service crea usuario, negocio y membresía."""

import httpx
import sqlalchemy as sa
from sqlalchemy.ext.asyncio import AsyncSession

from tests import fabricas


def _cuerpo(email: str | None = None) -> dict:
    return {
        "email": email or fabricas.correo_unico(),
        "password": fabricas.CONTRASENA_VALIDA,
        "nombre": "Marta",
        "negocio": {
            "nombre": "Papelería Marta",
            "moneda_base": "COP",
            "zona_horaria": "America/Bogota",
        },
    }


async def test_rf_aut_001_registro_crea_usuario_negocio_y_membresia_en_una_operacion(
    cliente: httpx.AsyncClient, sesion: AsyncSession
) -> None:
    respuesta = await cliente.post("/api/v1/auth/registro", json=_cuerpo())
    assert respuesta.status_code == 201, respuesta.text
    datos = respuesta.json()
    assert datos["token_acceso"]
    assert datos["tipo"] == "Bearer"
    assert datos["negocio"]["moneda_base"] == "COP"

    conteos = (
        await sesion.execute(
            sa.text(
                "select (select count(*) from usuarios) u, (select count(*) from negocios) n,"
                " (select count(*) from membresias where rol = 'dueno') m"
            )
        )
    ).one()
    assert (conteos.u, conteos.n, conteos.m) == (1, 1, 1)


async def test_rf_aut_001_correo_repetido_responde_409_sin_revelar_mas(
    cliente: httpx.AsyncClient,
) -> None:
    cuerpo = _cuerpo()
    await cliente.post("/api/v1/auth/registro", json=cuerpo)
    repetido = await cliente.post("/api/v1/auth/registro", json=cuerpo | {"nombre": "Otra"})
    assert repetido.status_code == 409
    error = repetido.json()["error"]
    assert error["code"] == "CORREO_YA_REGISTRADO"
    assert error["details"] == {}


async def test_rf_aut_001_el_correo_repetido_no_distingue_mayusculas(
    cliente: httpx.AsyncClient,
) -> None:
    correo = fabricas.correo_unico()
    await cliente.post("/api/v1/auth/registro", json=_cuerpo(correo))
    repetido = await cliente.post("/api/v1/auth/registro", json=_cuerpo(correo.upper()))
    assert repetido.status_code == 409


async def test_rnf_11_la_contrasena_se_guarda_con_argon2id(
    cliente: httpx.AsyncClient, sesion: AsyncSession
) -> None:
    cuerpo = _cuerpo()
    await cliente.post("/api/v1/auth/registro", json=cuerpo)
    hash_ = (await sesion.execute(sa.text("select password_hash from usuarios"))).scalar_one()
    assert hash_.startswith("$argon2id$")
    assert cuerpo["password"] not in hash_


async def test_rf_aut_004_moneda_base_invalida_responde_422(cliente: httpx.AsyncClient) -> None:
    cuerpo = _cuerpo()
    cuerpo["negocio"]["moneda_base"] = "pesos"
    respuesta = await cliente.post("/api/v1/auth/registro", json=cuerpo)
    assert respuesta.status_code == 422
    campos = {c["campo"] for c in respuesta.json()["error"]["details"]["campos"]}
    assert "negocio.moneda_base" in campos


async def test_rf_aut_001_contrasena_corta_responde_422(cliente: httpx.AsyncClient) -> None:
    respuesta = await cliente.post("/api/v1/auth/registro", json=_cuerpo() | {"password": "corta"})
    assert respuesta.status_code == 422
