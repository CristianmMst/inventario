import uuid
from typing import Annotated, Literal

from pydantic import BaseModel, StringConstraints

Texto = Annotated[str, StringConstraints(strip_whitespace=True, max_length=500)]
NombreProveedor = Annotated[
    str, StringConstraints(strip_whitespace=True, min_length=1, max_length=200)
]


class ProveedorNuevo(BaseModel):
    nombre: NombreProveedor
    identificacion_fiscal: Texto | None = None
    contacto: Texto | None = None
    telefono: Texto | None = None
    email: Texto | None = None
    direccion: Texto | None = None
    notas: Annotated[str, StringConstraints(strip_whitespace=True, max_length=2000)] | None = None


class ProveedorEdicion(BaseModel):
    nombre: NombreProveedor | None = None
    identificacion_fiscal: Texto | None = None
    contacto: Texto | None = None
    telefono: Texto | None = None
    email: Texto | None = None
    direccion: Texto | None = None
    notas: Annotated[str, StringConstraints(strip_whitespace=True, max_length=2000)] | None = None


class ProveedorSalida(BaseModel):
    id: uuid.UUID
    nombre: str
    identificacion_fiscal: str | None
    contacto: str | None
    telefono: str | None
    email: str | None
    direccion: str | None
    notas: str | None
    estado: Literal["activo", "archivado"]
