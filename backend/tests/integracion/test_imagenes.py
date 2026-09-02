"""RNF-05 y RF-CAT-006: subida de imágenes con límites verificados en el servidor."""

import io
import uuid

import httpx
import pytest
import sqlalchemy as sa
from PIL import Image
from sqlalchemy.ext.asyncio import AsyncSession

from app.dominio import errores as err
from app.dominio.imagenes import LIMITES, TipoImagen, validar_imagen
from tests import fabricas


def _jpeg(ancho: int, alto: int, calidad: int = 80) -> bytes:
    imagen = Image.effect_noise((ancho, alto), 64).convert("RGB")
    buffer = io.BytesIO()
    imagen.save(buffer, format="JPEG", quality=calidad)
    return buffer.getvalue()


def _png(ancho: int, alto: int) -> bytes:
    buffer = io.BytesIO()
    Image.new("RGB", (ancho, alto), (200, 30, 30)).save(buffer, format="PNG")
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


async def _producto(cliente: httpx.AsyncClient, auth: dict) -> dict:
    return (
        await cliente.post(
            "/api/v1/productos",
            json={"nombre": "Cuaderno", "unidad_codigo": "unidad"},
            headers=auth,
        )
    ).json()


async def _subir(
    cliente: httpx.AsyncClient,
    auth: dict,
    producto_id: str,
    contenido: bytes,
    mime: str = "image/jpeg",
) -> httpx.Response:
    return await cliente.put(
        f"/api/v1/productos/{producto_id}/imagen",
        files={"archivo": ("foto.jpg", contenido, mime)},
        headers=auth,
    )


class TestValidadorPuro:
    def test_rnf_05_limites_de_la_spec(self) -> None:
        assert LIMITES[TipoImagen.PRODUCTO] == (300 * 1024, 1280)
        assert LIMITES[TipoImagen.FACTURA] == (1536 * 1024, 2048)

    def test_rnf_05_producto_mayor_de_300_kb_se_rechaza(self) -> None:
        pesada = _jpeg(1200, 1200, calidad=100)
        assert len(pesada) > 300 * 1024
        with pytest.raises(err.ValidacionInvalida) as info:
            validar_imagen(pesada, TipoImagen.PRODUCTO)
        assert info.value.code == "IMAGEN_DEMASIADO_PESADA"

    def test_rnf_05_producto_mayor_de_1280_px_se_rechaza(self) -> None:
        with pytest.raises(err.ValidacionInvalida) as info:
            validar_imagen(_png(1281, 100), TipoImagen.PRODUCTO)
        assert info.value.code == "IMAGEN_DEMASIADO_GRANDE"
        assert info.value.details["lado_mayor"] == 1281

    def test_rnf_05_factura_admite_hasta_2048_px_y_1_5_mb(self) -> None:
        info = validar_imagen(_png(2048, 1000), TipoImagen.FACTURA)
        assert (info.ancho, info.alto) == (2048, 1000)
        with pytest.raises(err.ValidacionInvalida):
            validar_imagen(_png(2049, 100), TipoImagen.FACTURA)

    def test_rnf_05_un_archivo_que_no_es_imagen_se_rechaza(self) -> None:
        with pytest.raises(err.ValidacionInvalida) as info:
            validar_imagen(b"%PDF-1.4 no soy una imagen", TipoImagen.PRODUCTO)
        assert info.value.code == "IMAGEN_INVALIDA"

    def test_rnf_05_devuelve_mime_dimensiones_y_checksum(self) -> None:
        info = validar_imagen(_jpeg(640, 480), TipoImagen.PRODUCTO)
        assert info.mime == "image/jpeg" and (info.ancho, info.alto) == (640, 480)
        assert len(info.checksum) == 64


async def test_rf_cat_006_subir_la_foto_de_un_producto(
    cliente: httpx.AsyncClient, sesion: AsyncSession
) -> None:
    auth = await _sesion(cliente)
    p = await _producto(cliente, auth)
    r = await _subir(cliente, auth, p["id"], _jpeg(800, 600))
    assert r.status_code == 200, r.text
    imagen = r.json()["imagen"]
    assert imagen["id"] and imagen["url"].startswith("/api/v1/imagenes/")
    assert imagen["ancho"] == 800 and imagen["alto"] == 600 and imagen["mime"] == "image/jpeg"
    fila = (
        await sesion.execute(
            sa.text(
                "select tipo, clave_almacenamiento, bytes, checksum from imagenes where id = :id"
            ),
            {"id": imagen["id"]},
        )
    ).one()
    assert fila.tipo == "producto" and len(fila.clave_almacenamiento) >= 48 and fila.bytes > 0
    ficha = await cliente.get(f"/api/v1/productos/{p['id']}", headers=auth)
    assert ficha.json()["imagen"]["id"] == imagen["id"]


async def test_rf_cat_006_reemplazar_la_foto_deja_inaccesible_la_anterior(
    cliente: httpx.AsyncClient, sesion: AsyncSession
) -> None:
    auth = await _sesion(cliente)
    p = await _producto(cliente, auth)
    primera = (await _subir(cliente, auth, p["id"], _jpeg(300, 300))).json()["imagen"]
    segunda = (await _subir(cliente, auth, p["id"], _jpeg(400, 400))).json()["imagen"]
    assert primera["id"] != segunda["id"]
    quedan = (await sesion.execute(sa.text("select count(*) from imagenes"))).scalar_one()
    assert quedan == 1
    vieja = await cliente.get(f"/api/v1/imagenes/{primera['id']}", headers=auth)
    assert vieja.status_code == 404


async def test_rnf_05_el_servidor_rechaza_una_foto_de_producto_demasiado_grande(
    cliente: httpx.AsyncClient,
) -> None:
    auth = await _sesion(cliente)
    p = await _producto(cliente, auth)
    grande = await _subir(cliente, auth, p["id"], _png(1281, 720), "image/png")
    assert grande.status_code == 422
    assert grande.json()["error"]["code"] == "IMAGEN_DEMASIADO_GRANDE"
    pesada = await _subir(cliente, auth, p["id"], _jpeg(1200, 1200, calidad=100))
    assert pesada.status_code == 422
    assert pesada.json()["error"]["code"] == "IMAGEN_DEMASIADO_PESADA"
    ficha = await cliente.get(f"/api/v1/productos/{p['id']}", headers=auth)
    assert ficha.json()["imagen"] is None


async def test_rnf_05_el_mime_declarado_no_importa_se_inspecciona_el_contenido(
    cliente: httpx.AsyncClient,
) -> None:
    auth = await _sesion(cliente)
    p = await _producto(cliente, auth)
    r = await _subir(cliente, auth, p["id"], b"no soy una imagen", "image/jpeg")
    assert r.status_code == 422
    assert r.json()["error"]["code"] == "IMAGEN_INVALIDA"


async def test_rf_aut_007_no_se_sube_foto_a_un_producto_ajeno(cliente: httpx.AsyncClient) -> None:
    a, b = await _sesion(cliente), await _sesion(cliente)
    p = await _producto(cliente, a)
    assert (await _subir(cliente, b, p["id"], _jpeg(100, 100))).status_code == 404
    assert (await _subir(cliente, a, str(uuid.uuid4()), _jpeg(100, 100))).status_code == 404
