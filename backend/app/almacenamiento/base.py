"""Contrato del almacén de imágenes (plan.md §6). Los servicios dependen de este Protocol y
nunca de la implementación concreta; añadir S3 es implementar esta interfaz y cambiar una
variable de entorno."""

import re
import secrets
from datetime import timedelta
from typing import Protocol

_CLAVE_VALIDA = re.compile(r"^[0-9a-f]{48,}$")


class ImagenNoEncontrada(Exception):
    """La clave no existe en el almacén."""


def generar_clave() -> str:
    """Clave opaca y aleatoria: 256 bits en hex (RNF-11). No revela nada del contenido."""
    return secrets.token_hex(32)


def validar_clave(clave: str) -> str:
    """Solo se aceptan claves generadas aquí: nada de rutas, puntos ni separadores."""
    if not _CLAVE_VALIDA.match(clave):
        raise ValueError("clave de almacenamiento inválida")
    return clave


class AlmacenImagenes(Protocol):
    async def guardar(self, clave: str, contenido: bytes, mime: str) -> None: ...

    async def leer(self, clave: str) -> bytes: ...

    async def url_de_lectura(self, clave: str, ttl: timedelta) -> str: ...

    async def borrar(self, clave: str) -> None: ...
