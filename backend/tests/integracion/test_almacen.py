"""RNF-11: contrato del almacén de imágenes, escrito contra el Protocol, nunca contra la
implementación concreta. Ningún servicio importa el adaptador (plan.md §6)."""

import re
from datetime import timedelta
from pathlib import Path

import pytest

from app.almacenamiento import AlmacenImagenes, ImagenNoEncontrada, crear_almacen, generar_clave

RAIZ = Path(__file__).resolve().parents[2]


@pytest.fixture
def almacen(tmp_path: Path) -> AlmacenImagenes:
    return crear_almacen(tmp_path)


async def test_rnf_11_guardar_y_leer_devuelven_los_mismos_bytes(almacen: AlmacenImagenes) -> None:
    clave = generar_clave()
    await almacen.guardar(clave, b"\xff\xd8contenido", "image/jpeg")
    assert await almacen.leer(clave) == b"\xff\xd8contenido"


async def test_rnf_11_borrar_deja_la_clave_inexistente(almacen: AlmacenImagenes) -> None:
    clave = generar_clave()
    await almacen.guardar(clave, b"x", "image/jpeg")
    await almacen.borrar(clave)
    with pytest.raises(ImagenNoEncontrada):
        await almacen.leer(clave)


async def test_rnf_11_leer_una_clave_desconocida_falla_con_error_propio(
    almacen: AlmacenImagenes,
) -> None:
    with pytest.raises(ImagenNoEncontrada):
        await almacen.leer(generar_clave())


async def test_rnf_11_borrar_dos_veces_no_falla(almacen: AlmacenImagenes) -> None:
    clave = generar_clave()
    await almacen.guardar(clave, b"x", "image/jpeg")
    await almacen.borrar(clave)
    await almacen.borrar(clave)


def test_rnf_11_las_claves_son_opacas_aleatorias_y_no_adivinables() -> None:
    claves = {generar_clave() for _ in range(200)}
    assert len(claves) == 200
    for clave in claves:
        assert re.fullmatch(r"[0-9a-f]{48,}", clave), clave


async def test_rnf_11_una_clave_con_ruta_no_escapa_del_almacen(almacen: AlmacenImagenes) -> None:
    for maliciosa in ["../fuera", "/etc/passwd", "a/../../b", "..\\otra"]:
        with pytest.raises(ValueError):
            await almacen.guardar(maliciosa, b"x", "image/jpeg")


async def test_rnf_11_la_url_de_lectura_apunta_a_la_api_y_no_expone_la_clave(
    almacen: AlmacenImagenes,
) -> None:
    clave = generar_clave()
    await almacen.guardar(clave, b"x", "image/jpeg")
    url = await almacen.url_de_lectura(clave, timedelta(minutes=15))
    assert url.startswith("/api/v1/imagenes/")
    assert clave not in url


def test_plan_6_ningun_servicio_importa_el_adaptador_concreto() -> None:
    concreto = "Almacen" + "Filesystem"
    culpables = []
    for archivo in (RAIZ / "app" / "servicios").glob("*.py"):
        texto = archivo.read_text("utf-8")
        if concreto in texto or "almacenamiento.filesystem" in texto:
            culpables.append(archivo.name)
    for archivo in (RAIZ / "app" / "api").rglob("*.py"):
        texto = archivo.read_text("utf-8")
        if concreto in texto or "almacenamiento.filesystem" in texto:
            culpables.append(archivo.name)
    assert culpables == []


def test_plan_6_los_tests_del_almacen_no_mencionan_la_implementacion() -> None:
    concreto = "Almacen" + "Filesystem"
    fuente = Path(__file__).read_text("utf-8")
    assert concreto not in fuente
