import uuid
from datetime import date, datetime
from typing import Annotated, Literal

from pydantic import BaseModel, Field, StringConstraints

from app.esquemas.catalogo import DineroEntrada, DineroSalida, ImagenSalida
from app.esquemas.compras import ProveedorBreve, TasaCadena

EstadoPago = Literal["pendiente", "pagada", "anulada"]
Numero = Annotated[str, StringConstraints(strip_whitespace=True, min_length=1, max_length=64)]
Notas = Annotated[str, StringConstraints(strip_whitespace=True, max_length=2000)]


class FacturaNueva(BaseModel):
    """RF-FAC-001 / RF-FAC-003: base + impuesto = total exactamente (RN-18). Otra moneda que la
    base exige tasa_cambio."""

    proveedor_id: uuid.UUID
    numero: Numero
    fecha_emision: date
    fecha_vencimiento: date | None = None
    moneda: Annotated[str, StringConstraints(pattern=r"^[A-Z]{3}$")] | None = None
    tasa_cambio: TasaCadena | None = None
    base_gravable: DineroEntrada
    impuesto: DineroEntrada
    total: DineroEntrada
    notas: Notas | None = None
    recepciones: list[uuid.UUID] = Field(default_factory=list)


class FacturaEdicion(BaseModel):
    fecha_vencimiento: date | None = None
    notas: Notas | None = None


class PagoEntrada(BaseModel):
    """RF-FAC-004: marcar como pagada exige fecha de pago."""

    fecha_pago: date


class AnulacionFactura(BaseModel):
    motivo: Annotated[str, StringConstraints(strip_whitespace=True, min_length=1, max_length=1000)]


class RecepcionesVinculacion(BaseModel):
    """RF-FAC-006: sustituye el conjunto de recepciones vinculadas."""

    recepciones: list[uuid.UUID]


class RecepcionBreve(BaseModel):
    id: uuid.UUID
    numero: str
    fecha: date
    total: DineroSalida


class FacturaSalida(BaseModel):
    id: uuid.UUID
    proveedor: ProveedorBreve
    numero: str
    fecha_emision: date
    fecha_vencimiento: date | None
    moneda: str
    tasa_cambio: str
    base_gravable: DineroSalida
    impuesto: DineroSalida
    total: DineroSalida
    total_base: DineroSalida
    estado_pago: EstadoPago
    fecha_pago: date | None
    motivo_anulacion: str | None
    notas: str | None
    recepciones: list[RecepcionBreve]
    imagenes: list[ImagenSalida]
    created_at: datetime


class PaginaFacturas(BaseModel):
    """RF-FAC-008: además de la página, el total acumulado del filtro aplicado."""

    datos: list[FacturaSalida]
    cursor_siguiente: str | None
    tiene_mas: bool
    total_filtro: DineroSalida
    cantidad_filtro: int
