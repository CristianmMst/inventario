"""RNF-17 / RF-INT-008 (T-098): contrato documentado y paridad API/app.

- Toda operación figura en OpenAPI con seguridad declarada (o es pública a propósito), con
  ejemplo de petición cuando lleva cuerpo y con al menos una respuesta de error con el sobre
  `{"error": {...}}` de ejemplo.
- Toda ruta que usa la app Android (leída de `InventarioApi.kt`) existe en la API y acepta
  `X-API-Key`: no hay capacidad de la app sin endpoint de servicio equivalente.
"""

from __future__ import annotations

import re
from pathlib import Path

import pytest

from app.main import app

RAIZ = Path(__file__).resolve().parents[3]
API_ANDROID = (
    RAIZ
    / "android"
    / "core"
    / "data"
    / "src"
    / "main"
    / "kotlin"
    / "co"
    / "inventario"
    / "data"
    / "red"
    / "InventarioApi.kt"
)

# Rutas sin credencial por diseño: entrar, registrarse, renovar y la salud del servicio.
PUBLICAS = {
    ("POST", "/api/v1/auth/login"),
    ("POST", "/api/v1/auth/registro"),
    ("POST", "/api/v1/auth/refresh"),
    ("GET", "/api/v1/salud"),
    # Con `t` sirve la imagen firmada sin credencial (RNF-11); sin `t` la exige en el código.
    ("GET", "/api/v1/imagenes/{identificador}"),
}
# Operan sobre la sesión de un usuario: no tienen sentido con credencial de servicio.
SOLO_USUARIO = {
    ("POST", "/api/v1/auth/logout"),
    ("PATCH", "/api/v1/auth/password"),
}
METODOS = ("get", "post", "put", "patch", "delete")


@pytest.fixture(scope="module")
def documento() -> dict:
    return app.openapi()


def operaciones(doc: dict) -> list[tuple[str, str, dict]]:
    return [
        (m.upper(), ruta, op)
        for ruta, item in doc["paths"].items()
        for m, op in item.items()
        if m in METODOS
    ]


def test_rnf_17_toda_operacion_declara_seguridad_o_es_publica(documento: dict) -> None:
    esquemas = documento["components"]["securitySchemes"]
    assert esquemas["sesionUsuario"]["scheme"] == "bearer"
    servicio = esquemas["credencialServicio"]
    assert (servicio["type"], servicio["in"], servicio["name"]) == ("apiKey", "header", "X-API-Key")
    sin_seguridad = []
    for metodo, ruta, op in operaciones(documento):
        if (metodo, ruta) in PUBLICAS:
            assert not op.get("security"), f"{metodo} {ruta} es pública y no debe exigir credencial"
            continue
        requisitos = {list(r)[0] for r in op.get("security", [])}
        esperados = (
            {"sesionUsuario"}
            if (metodo, ruta) in SOLO_USUARIO
            else {"sesionUsuario", "credencialServicio"}
        )
        if requisitos != esperados:
            sin_seguridad.append(f"{metodo} {ruta}: {sorted(requisitos)}")
    assert not sin_seguridad, "operaciones con seguridad mal declarada:\n" + "\n".join(
        sin_seguridad
    )


def test_rnf_17_toda_peticion_con_cuerpo_trae_ejemplo(documento: dict) -> None:
    sin_ejemplo = []
    for metodo, ruta, op in operaciones(documento):
        cuerpo = op.get("requestBody")
        if not cuerpo:
            continue
        for contenido in cuerpo["content"].values():
            if "example" not in contenido and "examples" not in contenido:
                sin_ejemplo.append(f"{metodo} {ruta}")
    assert not sin_ejemplo, "peticiones sin ejemplo:\n" + "\n".join(sin_ejemplo)


def test_rnf_17_toda_operacion_documenta_un_error_con_el_sobre_unico(documento: dict) -> None:
    sin_error = []
    for metodo, ruta, op in operaciones(documento):
        if (metodo, ruta) == ("GET", "/api/v1/salud"):
            continue
        ejemplos_error = []
        for codigo, respuesta in op["responses"].items():
            if not codigo.startswith("4"):
                continue
            for contenido in respuesta.get("content", {}).values():
                ejemplo = contenido.get("example")
                if ejemplo and set(ejemplo.get("error", {})) >= {"code", "message", "details"}:
                    ejemplos_error.append(codigo)
        if not ejemplos_error:
            sin_error.append(f"{metodo} {ruta}")
        if (metodo, ruta) not in PUBLICAS and "401" not in op["responses"]:
            sin_error.append(f"{metodo} {ruta} sin 401")
    assert not sin_error, "operaciones sin error de ejemplo:\n" + "\n".join(sin_error)


def rutas_de_la_app() -> set[tuple[str, str]]:
    texto = API_ANDROID.read_text(encoding="utf-8")
    rutas = set()
    for metodo, ruta in re.findall(r'@(GET|POST|PUT|PATCH|DELETE)\("([^"]+)"\)', texto):
        rutas.add((metodo, "/" + re.sub(r"\{[^}]+}", "{}", ruta)))
    return rutas


def test_rf_int_008_toda_ruta_de_la_app_existe_y_acepta_credencial_de_servicio(
    documento: dict,
) -> None:
    del_servidor = {
        (m, re.sub(r"\{[^}]+}", "{}", ruta)): op for m, ruta, op in operaciones(documento)
    }
    faltantes = []
    sin_api_key = []
    app_rutas = rutas_de_la_app()
    assert len(app_rutas) >= 60, (
        f"la interfaz Retrofit tiene menos rutas de las esperadas: {len(app_rutas)}"
    )
    for metodo, ruta in sorted(app_rutas):
        op = del_servidor.get((metodo, ruta))
        if op is None:
            faltantes.append(f"{metodo} {ruta}")
            continue
        publica = (metodo, ruta) in {(m, re.sub(r"\{[^}]+}", "{}", r)) for m, r in PUBLICAS}
        solo_usuario = (metodo, ruta) in {
            (m, re.sub(r"\{[^}]+}", "{}", r)) for m, r in SOLO_USUARIO
        }
        acepta = any("credencialServicio" in r for r in op.get("security", []))
        if not (publica or solo_usuario or acepta):
            sin_api_key.append(f"{metodo} {ruta}")
    assert not faltantes, "la app usa rutas que la API no publica:\n" + "\n".join(faltantes)
    assert not sin_api_key, "capacidades de la app sin credencial de servicio:\n" + "\n".join(
        sin_api_key
    )
