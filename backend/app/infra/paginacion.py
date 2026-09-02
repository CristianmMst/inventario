"""Paginación por cursor (constitution.md §2). Toda colección la usa; `limit` máximo 100.

El cursor es opaco para el cliente: base64url de un JSON con la clave de ordenación de la
última fila devuelta (keyset). Al paginar por clave y no por desplazamiento, insertar filas
entre una página y la siguiente no duplica ni salta registros.
"""

import base64
import binascii
import json
from collections.abc import Callable
from typing import Any, TypeVar

from pydantic import BaseModel, Field

from app.dominio import errores as err

LIMITE_POR_DEFECTO = 50
LIMITE_MAXIMO = 100

T = TypeVar("T")


class ParametrosPagina(BaseModel):
    cursor: str | None = None
    limit: int = Field(default=LIMITE_POR_DEFECTO, ge=1, le=LIMITE_MAXIMO)


class Pagina[T](BaseModel):
    datos: list[T]
    cursor_siguiente: str | None
    tiene_mas: bool


def codificar_cursor(clave: dict[str, Any]) -> str:
    crudo = json.dumps(clave, separators=(",", ":"), sort_keys=True).encode()
    return base64.urlsafe_b64encode(crudo).decode().rstrip("=")


def decodificar_cursor(cursor: str) -> dict[str, Any]:
    try:
        relleno = "=" * (-len(cursor) % 4)
        crudo = base64.urlsafe_b64decode(cursor + relleno)
        clave = json.loads(crudo)
    except (binascii.Error, ValueError, UnicodeDecodeError) as e:
        raise err.ValidacionInvalida(
            "CURSOR_INVALIDO",
            "El cursor de paginación no es válido. Empieza desde la primera página.",
        ) from e
    if not isinstance(clave, dict):
        raise err.ValidacionInvalida(
            "CURSOR_INVALIDO",
            "El cursor de paginación no es válido. Empieza desde la primera página.",
        )
    return clave


def paginar[T](filas: list[T], limit: int, *, clave_de: Callable[[T], dict[str, Any]]) -> Pagina[T]:
    """Recibe hasta `limit + 1` filas ya ordenadas y arma la página.

    La fila sobrante solo sirve para saber que hay más; el cursor apunta a la última fila
    devuelta, no a la sobrante.
    """
    tiene_mas = len(filas) > limit
    datos = filas[:limit]
    cursor = codificar_cursor(clave_de(datos[-1])) if tiene_mas and datos else None
    return Pagina(datos=datos, cursor_siguiente=cursor, tiene_mas=tiene_mas)
