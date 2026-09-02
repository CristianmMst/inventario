import uuid
from datetime import datetime
from typing import Annotated

from pydantic import BaseModel, Field, HttpUrl, StringConstraints, field_validator


class SuscripcionNueva(BaseModel):
    """RF-INT-005: URL de destino (HTTPS), tipos de evento o comodín `*`, y secreto de firma."""

    url: HttpUrl
    tipos: Annotated[list[str], Field(min_length=1, max_length=50)]
    secreto: Annotated[str, StringConstraints(min_length=32, max_length=256)]
    descripcion: Annotated[str, StringConstraints(strip_whitespace=True, max_length=500)] | None = (
        None
    )

    @field_validator("url")
    @classmethod
    def _solo_https(cls, url: HttpUrl) -> HttpUrl:
        if url.scheme != "https":
            raise ValueError("la URL del webhook debe ser https")
        return url


class SuscripcionSalida(BaseModel):
    id: uuid.UUID
    url: str
    tipos: list[str]
    activa: bool
    descripcion: str | None
    created_at: datetime
