import uuid
from typing import Annotated, Literal

from pydantic import BaseModel, StringConstraints


class UnidadMedidaSalida(BaseModel):
    codigo: str
    nombre: str
    tipo: Literal["discreta", "continua"]
    decimales: int


NombreCategoria = Annotated[
    str, StringConstraints(strip_whitespace=True, min_length=1, max_length=100)
]


class CategoriaNueva(BaseModel):
    nombre: NombreCategoria


class CategoriaEdicion(BaseModel):
    nombre: NombreCategoria


class CategoriaSalida(BaseModel):
    id: uuid.UUID
    nombre: str
