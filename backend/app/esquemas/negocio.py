import uuid
from typing import Literal

from pydantic import BaseModel


class CredencialSalida(BaseModel):
    tipo: Literal["usuario", "servicio"]
    id: uuid.UUID


class NegocioActual(BaseModel):
    id: uuid.UUID
    nombre: str
    moneda_base: str
    zona_horaria: str
    credencial: CredencialSalida
