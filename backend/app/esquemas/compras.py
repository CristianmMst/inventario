import uuid
from datetime import date, datetime
from typing import Annotated, Literal

from pydantic import BaseModel, Field, StringConstraints

from app.esquemas.catalogo import CantidadCadena, DineroEntrada, DineroSalida

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


class ProveedorBreve(BaseModel):
    id: uuid.UUID
    nombre: str


class ProductoBreve(BaseModel):
    id: uuid.UUID
    nombre: str
    sku: str
    unidad_codigo: str


class LineaOrdenEntrada(BaseModel):
    producto_id: uuid.UUID
    cantidad: CantidadCadena
    costo_unitario_estimado: DineroEntrada | None = None


class OrdenNueva(BaseModel):
    """RF-COM-002: proveedor, fecha esperada, moneda (la base por defecto), notas y líneas."""

    proveedor_id: uuid.UUID
    fecha_esperada: date | None = None
    moneda: Annotated[str, StringConstraints(pattern=r"^[A-Z]{3}$")] | None = None
    notas: Annotated[str, StringConstraints(strip_whitespace=True, max_length=2000)] | None = None
    lineas: Annotated[list[LineaOrdenEntrada], Field(min_length=1)]


class OrdenEdicion(BaseModel):
    """Solo en borrador (RF-COM-003). `lineas` sustituye todas las líneas."""

    fecha_esperada: date | None = None
    notas: Annotated[str, StringConstraints(strip_whitespace=True, max_length=2000)] | None = None
    lineas: Annotated[list[LineaOrdenEntrada], Field(min_length=1)] | None = None


class LineaOrdenSalida(BaseModel):
    id: uuid.UUID
    producto: ProductoBreve
    cantidad_ordenada: str
    costo_unitario_estimado: DineroSalida | None
    cantidad_recibida: str
    cantidad_pendiente: str


EstadoOrden = Literal[
    "borrador", "emitida", "parcialmente_recibida", "recibida", "cerrada_con_faltante", "cancelada"
]


class OrdenSalida(BaseModel):
    id: uuid.UUID
    numero: str
    proveedor: ProveedorBreve
    estado: EstadoOrden
    fecha_esperada: date | None
    moneda: str
    notas: str | None
    motivo_cierre: str | None
    emitida_en: datetime | None
    cerrada_en: datetime | None
    lineas: list[LineaOrdenSalida]
    total_estimado: DineroSalida | None
    created_at: datetime


class CancelacionEntrada(BaseModel):
    """RF-COM-010: cancelar exige motivo."""

    motivo: Annotated[str, StringConstraints(strip_whitespace=True, min_length=1, max_length=1000)]
