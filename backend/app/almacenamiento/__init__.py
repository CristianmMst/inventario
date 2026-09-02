"""Almacén de imágenes: un Protocol y una fábrica. Los servicios importan solo de aquí."""

from pathlib import Path

from app.almacenamiento.base import (
    AlmacenImagenes,
    ImagenNoEncontrada,
    generar_clave,
    validar_clave,
)
from app.config import obtener_ajustes


def crear_almacen(directorio: Path | None = None) -> AlmacenImagenes:
    """Fábrica: la única que conoce la implementación concreta."""
    from app.almacenamiento.filesystem import AlmacenFilesystem

    return AlmacenFilesystem(directorio or obtener_ajustes().imagenes_dir)


__all__ = [
    "AlmacenImagenes",
    "ImagenNoEncontrada",
    "crear_almacen",
    "generar_clave",
    "validar_clave",
]
