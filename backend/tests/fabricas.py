"""Fábricas de datos de prueba. Crecen con cada hito; aquí solo lo transversal."""

import itertools
import uuid

_contador = itertools.count(1)


def correo_unico() -> str:
    return f"prueba-{uuid.uuid4().hex[:12]}@ejemplo.test"


def nombre_unico(prefijo: str) -> str:
    return f"{prefijo} {next(_contador):05d}-{uuid.uuid4().hex[:6]}"


CONTRASENA_VALIDA = "Contrasena-segura-123"
