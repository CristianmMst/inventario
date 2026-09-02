import uuid
from datetime import datetime
from typing import Annotated, Literal

from pydantic import BaseModel, StringConstraints

from app.esquemas.catalogo import CantidadCadena


class MotivoSalida(BaseModel):
    codigo: str
    tipo_movimiento: Literal["entrada", "salida", "ajuste", "merma", "contramovimiento"]
    etiqueta: str
    exige_nota: bool


class AutorSalida(BaseModel):
    tipo: Literal["usuario", "servicio"]
    id: uuid.UUID


class MovimientoNuevo(BaseModel):
    """RF-INV-001/002. La cantidad es cadena decimal positiva; el sentido lo da `tipo`, y para
    `ajuste` la `direccion`. `forzar` es el override explícito de RF-INV-006."""

    producto_id: uuid.UUID
    tipo: Literal["entrada", "salida", "ajuste", "merma", "contramovimiento"]
    cantidad: CantidadCadena
    motivo: Annotated[str, StringConstraints(strip_whitespace=True, min_length=1, max_length=32)]
    nota: Annotated[str, StringConstraints(strip_whitespace=True, max_length=1000)] | None = None
    forzar: bool = False
    direccion: Literal[-1, 1] | None = None


class MovimientoSalida(BaseModel):
    id: uuid.UUID
    producto_id: uuid.UUID
    tipo: Literal["entrada", "salida", "ajuste", "merma", "contramovimiento"]
    cantidad: str
    direccion: Literal[-1, 1]
    motivo: str
    nota: str | None
    forzado: bool
    stock_resultante: str
    origen: Literal["app", "api", "recepcion"]
    autor: AutorSalida
    ocurrido_en: datetime
    anulado_en: datetime | None
    anula_movimiento_id: uuid.UUID | None
    recepcion_linea_id: uuid.UUID | None


class StockSalida(BaseModel):
    producto_id: uuid.UUID
    cantidad: str
    actualizado_en: datetime | None


class AnulacionEntrada(BaseModel):
    """RF-INV-008: la anulación exige motivo escrito (RN-02); el motivo fijo es `anulacion`."""

    nota: (
        Annotated[str, StringConstraints(strip_whitespace=True, min_length=1, max_length=1000)]
        | None
    ) = None


class ConteoEntrada(BaseModel):
    """RF-INV-013 / RN-15: se declara la cantidad contada; el servidor calcula el delta."""

    cantidad_contada: CantidadCadena
    nota: Annotated[str, StringConstraints(strip_whitespace=True, max_length=1000)] | None = None


class ConteoSalida(BaseModel):
    producto_id: uuid.UUID
    stock_anterior: str
    cantidad_contada: str
    diferencia: str
    movimiento: MovimientoSalida | None
