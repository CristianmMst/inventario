"""Enriquecimiento del documento OpenAPI (RNF-17, RF-INT-008).

FastAPI genera rutas y esquemas; aquí se añade lo que el contrato exige en el 100% de las
operaciones: los dos esquemas de seguridad (sesión de usuario y credencial de servicio), la
seguridad por operación, un ejemplo de petición para todo cuerpo y respuestas de error con el
sobre único `{"error": {"code", "message", "details"}}` (constitution.md §3). Nada de esto
cambia el comportamiento de la API: solo lo documenta.
"""

from __future__ import annotations

import copy
from typing import Any

from fastapi import FastAPI
from fastapi.openapi.utils import get_openapi

SESION = "sesionUsuario"
SERVICIO = "credencialServicio"

# Sin credencial por diseño (RNF-11): entrar, registrarse, renovar, salud e imagen firmada.
PUBLICAS = {
    ("post", "/api/v1/auth/login"),
    ("post", "/api/v1/auth/registro"),
    ("post", "/api/v1/auth/refresh"),
    ("get", "/api/v1/salud"),
    ("get", "/api/v1/imagenes/{identificador}"),
}
# Manipulan la sesión de un usuario: una credencial de servicio no tiene sesión que cerrar.
SOLO_USUARIO = {("post", "/api/v1/auth/logout"), ("patch", "/api/v1/auth/password")}
METODOS = ("get", "post", "put", "patch", "delete")

UUID_EJEMPLO = "8b1c2a7e-4f3d-4c1b-9e2a-6d5f7a8b9c0d"


def sobre_error(code: str, message: str, details: dict[str, Any] | None = None) -> dict[str, Any]:
    return {"error": {"code": code, "message": message, "details": details or {}}}


ERRORES = {
    "401": sobre_error("CREDENCIAL_REQUERIDA", "Debes iniciar sesión."),
    "404": sobre_error("RECURSO_NO_ENCONTRADO", "Eso ya no existe."),
    "409": sobre_error("CONFLICTO", "La operación choca con el estado actual.", {"detalle": "…"}),
    "422": sobre_error("VALIDACION", "Revisa los datos marcados.", {"campo": "mensaje"}),
}
DESCRIPCIONES = {
    "401": "Sin credencial válida: falta `Authorization: Bearer` o `X-API-Key`, o caducó.",
    "404": "No existe o pertenece a otro negocio (RF-AUT-007: nunca se distingue).",
    "409": "Conflicto de negocio; `details` explica qué chocó.",
    "422": "Cuerpo o parámetros inválidos; `details` va campo por campo.",
}


def _ejemplo_desde_esquema(
    esquema: dict[str, Any], componentes: dict[str, Any], profundidad: int = 0
) -> Any:
    """Construye un ejemplo plausible a partir del JSON Schema; respeta `examples`/`default`."""
    if "$ref" in esquema:
        nombre = esquema["$ref"].rsplit("/", 1)[-1]
        return _ejemplo_desde_esquema(componentes.get(nombre, {}), componentes, profundidad + 1)
    if "examples" in esquema and esquema["examples"]:
        return esquema["examples"][0]
    if "example" in esquema:
        return esquema["example"]
    if "const" in esquema:
        return esquema["const"]
    if "enum" in esquema:
        return esquema["enum"][0]
    for clave in ("anyOf", "oneOf", "allOf"):
        if clave in esquema:
            opciones = [o for o in esquema[clave] if o.get("type") != "null"] or esquema[clave]
            return _ejemplo_desde_esquema(opciones[0], componentes, profundidad + 1)
    if "default" in esquema and esquema["default"] is not None:
        return esquema["default"]
    tipo = esquema.get("type")
    if isinstance(tipo, list):
        tipo = next((t for t in tipo if t != "null"), "string")
    if tipo == "object" or "properties" in esquema:
        if profundidad > 6:
            return {}
        return {
            nombre: _ejemplo_desde_esquema(sub, componentes, profundidad + 1)
            for nombre, sub in esquema.get("properties", {}).items()
        }
    if tipo == "array":
        return [_ejemplo_desde_esquema(esquema.get("items", {}), componentes, profundidad + 1)]
    if tipo == "integer":
        return int(esquema.get("minimum", 1)) or 1
    if tipo == "number":
        return 1
    if tipo == "boolean":
        return False
    formato = esquema.get("format")
    patron = esquema.get("pattern", "")
    if formato == "uuid":
        return UUID_EJEMPLO
    if formato == "date":
        return "2026-09-02"
    if formato == "date-time":
        return "2026-09-02T10:00:00+00:00"
    if formato == "email":
        return "marta@ejemplo.com"
    if formato == "uri":
        return "https://ejemplo.com/webhook"
    if formato == "binary":
        return "<bytes del archivo>"
    if patron.startswith("^[A-Z]{3}$"):
        return "COP"
    if "\\d" in patron and "\\." in patron:
        return "2500.0000"
    minimo = esquema.get("minLength", 0)
    texto = "texto de ejemplo"
    return texto if len(texto) >= minimo else texto.ljust(minimo, "x")


def _asegurar_respuesta_error(op: dict[str, Any], codigo: str) -> None:
    respuesta = op.setdefault("responses", {}).setdefault(
        codigo, {"description": DESCRIPCIONES[codigo]}
    )
    respuesta.setdefault("description", DESCRIPCIONES[codigo])
    contenido = respuesta.setdefault("content", {}).setdefault("application/json", {})
    contenido.setdefault("example", ERRORES[codigo])


def enriquecer_openapi(app: FastAPI) -> dict[str, Any]:
    if app.openapi_schema:
        return app.openapi_schema
    doc = get_openapi(
        title=app.title, version=app.version, description=app.description, routes=app.routes
    )
    componentes = doc.setdefault("components", {})
    componentes["securitySchemes"] = {
        SESION: {
            "type": "http",
            "scheme": "bearer",
            "bearerFormat": "JWT",
            "description": "Token de acceso de 15 minutos (RF-AUT-002).",
        },
        SERVICIO: {
            "type": "apiKey",
            "in": "header",
            "name": "X-API-Key",
            "description": "Credencial de servicio `inv_<prefijo>_<secreto>` (RF-AUT-005).",
        },
    }
    esquemas = componentes.get("schemas", {})

    for ruta, item in doc["paths"].items():
        for metodo, op in item.items():
            if metodo not in METODOS:
                continue
            clave = (metodo, ruta)
            if clave in PUBLICAS:
                op.pop("security", None)
            elif clave in SOLO_USUARIO:
                op["security"] = [{SESION: []}]
                _asegurar_respuesta_error(op, "401")
            else:
                op["security"] = [{SESION: []}, {SERVICIO: []}]
                _asegurar_respuesta_error(op, "401")
            if "{" in ruta:
                _asegurar_respuesta_error(op, "404")
            if "409" in op.get("responses", {}):
                _asegurar_respuesta_error(op, "409")
            if clave != ("get", "/api/v1/salud"):
                _asegurar_respuesta_error(op, "422")
            cuerpo = op.get("requestBody")
            if cuerpo:
                for contenido in cuerpo.get("content", {}).values():
                    if "example" not in contenido and "examples" not in contenido:
                        contenido["example"] = _ejemplo_desde_esquema(
                            copy.deepcopy(contenido.get("schema", {})), esquemas
                        )
    app.openapi_schema = doc
    return doc
