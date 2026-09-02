import uuid
from datetime import datetime
from typing import Annotated

from pydantic import BaseModel, Field


class ApiKeyNueva(BaseModel):
    nombre: Annotated[str, Field(min_length=1, max_length=100)]


class ApiKeySalida(BaseModel):
    id: uuid.UUID
    nombre: str
    prefijo: str
    created_at: datetime
    ultimo_uso_en: datetime | None
    revocado_en: datetime | None


class ApiKeyCreada(ApiKeySalida):
    """Única respuesta que incluye la clave completa. No vuelve a mostrarse (RF-AUT-005)."""

    clave: str
