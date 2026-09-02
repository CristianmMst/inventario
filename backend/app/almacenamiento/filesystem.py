"""Única implementación de v1: archivos bajo IMAGENES_DIR (plan.md §0 y §6)."""

import asyncio
from datetime import timedelta
from pathlib import Path

from app.almacenamiento.base import ImagenNoEncontrada, validar_clave
from app.infra.firmas import firmar_lectura


class AlmacenFilesystem:
    def __init__(self, directorio: Path) -> None:
        self._dir = directorio

    def _ruta(self, clave: str) -> Path:
        validar_clave(clave)
        return self._dir / clave[:2] / clave

    async def guardar(self, clave: str, contenido: bytes, mime: str) -> None:
        ruta = self._ruta(clave)

        def _escribir() -> None:
            ruta.parent.mkdir(parents=True, exist_ok=True)
            temporal = ruta.with_suffix(".parcial")
            temporal.write_bytes(contenido)
            temporal.replace(ruta)

        await asyncio.to_thread(_escribir)

    async def leer(self, clave: str) -> bytes:
        ruta = self._ruta(clave)
        try:
            return await asyncio.to_thread(ruta.read_bytes)
        except FileNotFoundError as e:
            raise ImagenNoEncontrada(clave) from e

    async def url_de_lectura(self, clave: str, ttl: timedelta) -> str:
        """La URL la sirve la API con un token HMAC caducable (RNF-11); la clave no viaja."""
        return firmar_lectura(clave, ttl)

    async def borrar(self, clave: str) -> None:
        ruta = self._ruta(clave)
        await asyncio.to_thread(ruta.unlink, True)
