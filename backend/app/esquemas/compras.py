import uuid
from datetime import date, datetime
from typing import Annotated, Literal

from pydantic import BaseModel, Field, StrictStr, StringConstraints

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


TasaCadena = Annotated[StrictStr, StringConstraints(pattern=r"^\d{1,10}(\.\d{1,8})?$")]


class LineaRecepcionEntrada(BaseModel):
    producto_id: uuid.UUID
    cantidad: CantidadCadena
    costo_unitario: DineroEntrada


class RecepcionNueva(BaseModel):
    """RF-COM-004/005: proveedor y líneas; orden opcional (RN-11). Otra moneda que la base
    exige tasa_cambio (RF-COM-007)."""

    proveedor_id: uuid.UUID
    orden_id: uuid.UUID | None = None
    fecha: date | None = None
    moneda: Annotated[str, StringConstraints(pattern=r"^[A-Z]{3}$")] | None = None
    tasa_cambio: TasaCadena | None = None
    notas: Annotated[str, StringConstraints(strip_whitespace=True, max_length=2000)] | None = None
    lineas: Annotated[list[LineaRecepcionEntrada], Field(min_length=1)]


class RecepcionEdicion(BaseModel):
    fecha: date | None = None
    tasa_cambio: TasaCadena | None = None
    notas: Annotated[str, StringConstraints(strip_whitespace=True, max_length=2000)] | None = None
    lineas: Annotated[list[LineaRecepcionEntrada], Field(min_length=1)] | None = None


class ConfirmacionRecepcion(BaseModel):
    """RF-COM-009: recibir de mas solo se acepta con confirmacion explicita."""

    confirmar_exceso: bool = False


class OrdenBreve(BaseModel):
    id: uuid.UUID
    numero: str


class LineaRecepcionSalida(BaseModel):
    id: uuid.UUID
    producto: ProductoBreve
    orden_linea_id: uuid.UUID | None
    cantidad_recibida: str
    costo_unitario: DineroSalida
    tasa_cambio: str | None
    costo_unitario_base: DineroSalida | None
    exceso: bool


class RecepcionSalida(BaseModel):
    id: uuid.UUID
    numero: str
    proveedor: ProveedorBreve
    orden: OrdenBreve | None
    estado: Literal["borrador", "confirmada", "corregida"]
    fecha: date
    moneda: str
    tasa_cambio: str
    notas: str | None
    confirmada_en: datetime | None
    lineas: list[LineaRecepcionSalida]
    total: DineroSalida
    total_base: DineroSalida | None
    movimientos_generados: list[uuid.UUID]
    created_at: datetime


class CierreFaltanteEntrada(BaseModel):
    """RF-COM-008: cerrar con faltante indica el motivo."""

    motivo: Annotated[str, StringConstraints(strip_whitespace=True, min_length=1, max_length=1000)]
