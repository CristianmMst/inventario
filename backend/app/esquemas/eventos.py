import uuid
from datetime import datetime
from typing import Any, Literal

from pydantic import BaseModel


class AutorEvento(BaseModel):
    tipo: Literal["usuario", "servicio"]
    id: uuid.UUID
    nombre: str


class EventoSalida(BaseModel):
    """Sobre común de RF-INT-003 más `secuencia`, el cursor de RF-INT-004."""

    id: uuid.UUID
    secuencia: int
    tipo: str
    version: int
    business_id: uuid.UUID
    ocurrido_en: datetime
    autor: AutorEvento
    payload: dict[str, Any]
