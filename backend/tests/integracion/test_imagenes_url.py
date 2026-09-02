"""RNF-11 y RNF-16: URL de lectura firmada con HMAC y caducidad; nada se depura solo."""

import io
import re
from datetime import timedelta
from pathlib import Path

import httpx
from PIL import Image

from app.infra.firmas import firmar_lectura
from tests import fabricas

RAIZ = Path(__file__).resolve().parents[2]


def _jpeg() -> bytes:
    buffer = io.BytesIO()
    Image.new("RGB", (64, 64), (10, 200, 10)).save(buffer, format="JPEG")
    return buffer.getvalue()


async def _sesion(cliente: httpx.AsyncClient) -> dict:
    cuerpo = {
        "email": fabricas.correo_unico(),
        "password": fabricas.CONTRASENA_VALIDA,
        "nombre": "Marta",
        "negocio": {"nombre": "P", "moneda_base": "COP", "zona_horaria": "UTC"},
    }
    r = await cliente.post("/api/v1/auth/registro", json=cuerpo)
    return {"Authorization": f"Bearer {r.json()['token_acceso']}"}


async def _producto_con_foto(cliente: httpx.AsyncClient, auth: dict) -> dict:
    p = (
        await cliente.post(
            "/api/v1/productos",
            json={"nombre": "Cuaderno", "unidad_codigo": "unidad"},
            headers=auth,
        )
    ).json()
    r = await cliente.put(
        f"/api/v1/productos/{p['id']}/imagen",
        files={"archivo": ("foto.jpg", _jpeg(), "image/jpeg")},
        headers=auth,
    )
    assert r.status_code == 200, r.text
    return r.json()


async def test_rnf_11_get_imagenes_con_credencial_redirige_a_una_url_firmada(
    cliente: httpx.AsyncClient,
) -> None:
    auth = await _sesion(cliente)
    p = await _producto_con_foto(cliente, auth)
    r = await cliente.get(f"/api/v1/imagenes/{p['imagen']['id']}", headers=auth)
    assert r.status_code == 307
    destino = r.headers["location"]
    assert re.match(r"^/api/v1/imagenes/[0-9a-f]{32}\?t=[A-Za-z0-9_\-]+\.[0-9a-f]{64}$", destino)
    assert p["imagen"]["id"] not in destino


async def test_rnf_11_la_url_firmada_sirve_los_bytes_sin_credencial(
    cliente: httpx.AsyncClient,
) -> None:
    auth = await _sesion(cliente)
    p = await _producto_con_foto(cliente, auth)
    r = await cliente.get(p["imagen"]["url"])
    assert r.status_code == 200
    assert r.headers["content-type"] == "image/jpeg"
    assert r.content[:2] == b"\xff\xd8"


async def test_rnf_11_la_clave_no_es_adivinable_ni_viaja_en_la_url(
    cliente: httpx.AsyncClient,
) -> None:
    auth = await _sesion(cliente)
    p = await _producto_con_foto(cliente, auth)
    url = p["imagen"]["url"]
    identificador = url.split("/")[4].split("?")[0]
    assert len(identificador) == 32
    sin_token = await cliente.get(f"/api/v1/imagenes/{identificador}")
    assert sin_token.status_code == 401
    token_ajeno = await cliente.get(f"/api/v1/imagenes/{identificador}?t=abc.def")
    assert token_ajeno.status_code == 404


async def test_rnf_11_una_url_caducada_no_sirve(cliente: httpx.AsyncClient) -> None:
    auth = await _sesion(cliente)
    p = await _producto_con_foto(cliente, auth)
    vigente = await cliente.get(p["imagen"]["url"])
    assert vigente.status_code == 200
    # Misma imagen, token generado con caducidad ya pasada.
    clave = None
    import sqlalchemy as sa

    from app.infra.db import fabrica_sesiones

    async with fabrica_sesiones()() as s:
        clave = (
            await s.execute(
                sa.text("select clave_almacenamiento from imagenes where id = :id"),
                {"id": p["imagen"]["id"]},
            )
        ).scalar_one()
    caducada = firmar_lectura(clave, timedelta(seconds=-1))
    r = await cliente.get(caducada)
    assert r.status_code == 404


async def test_rnf_11_un_token_valido_no_abre_otra_imagen(cliente: httpx.AsyncClient) -> None:
    auth = await _sesion(cliente)
    a = await _producto_con_foto(cliente, auth)
    b = await _producto_con_foto(cliente, auth)
    token_de_a = a["imagen"]["url"].split("?t=")[1]
    identificador_b = b["imagen"]["url"].split("/")[4].split("?")[0]
    r = await cliente.get(f"/api/v1/imagenes/{identificador_b}?t={token_de_a}")
    assert r.status_code == 404


def test_rnf_16_nada_se_depura_automaticamente() -> None:
    """No hay tareas programadas ni borrados por antigüedad en el código."""
    sospechosos = re.compile(
        r"(depurar|purgar|retencion|cron|crontab|scheduler|apscheduler|celery)", re.I
    )
    culpables = [
        str(a.relative_to(RAIZ))
        for a in (RAIZ / "app").rglob("*.py")
        if sospechosos.search(
            a.read_text("utf-8").replace("Nada se depura", "").replace("nada se depura", "")
        )
    ]
    assert culpables == []
