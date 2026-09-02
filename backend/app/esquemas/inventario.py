from typing import Literal

from pydantic import BaseModel


class MotivoSalida(BaseModel):
    codigo: str
    tipo_movimiento: Literal["entrada", "salida", "ajuste", "merma", "contramovimiento"]
    etiqueta: str
    exige_nota: bool
