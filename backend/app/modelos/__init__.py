"""Importa todos los modelos para que `Base.metadata` los conozca (Alembic y TRUNCATE)."""

from app.modelos import (
    catalogo,
    compras,
    eventos,
    facturas,
    identidad,
    imagenes,
    inventario,
    sistema,
)
from app.modelos.base import Base

__all__ = [
    "Base",
    "catalogo",
    "compras",
    "eventos",
    "facturas",
    "identidad",
    "imagenes",
    "inventario",
    "sistema",
]
