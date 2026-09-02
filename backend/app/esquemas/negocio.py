import uuid
from typing import Annotated, Literal

from pydantic import BaseModel, StringConstraints


class CredencialSalida(BaseModel):
    tipo: Literal["usuario", "servicio"]
    id: uuid.UUID


class NegocioActual(BaseModel):
    id: uuid.UUID
    nombre: str
    moneda_base: str
    zona_horaria: str
    credencial: CredencialSalida


class NegocioEdicion(BaseModel):
    """RF-AUT-004: la moneda base no cambia si ya hay documentos valorizados (RN-10)."""

    nombre: (
        Annotated[str, StringConstraints(strip_whitespace=True, min_length=1, max_length=200)]
        | None
    ) = None
    zona_horaria: Annotated[str, StringConstraints(min_length=1, max_length=64)] | None = None
    moneda_base: Annotated[str, StringConstraints(pattern=r"^[A-Z]{3}$")] | None = None
