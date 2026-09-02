from typing import Literal

from pydantic import BaseModel


class UnidadMedidaSalida(BaseModel):
    codigo: str
    nombre: str
    tipo: Literal["discreta", "continua"]
    decimales: int
