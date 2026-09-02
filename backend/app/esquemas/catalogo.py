import uuid
from typing import Annotated, Literal

from pydantic import BaseModel, StrictStr, StringConstraints


class UnidadMedidaSalida(BaseModel):
    codigo: str
    nombre: str
    tipo: Literal["discreta", "continua"]
    decimales: int


NombreCategoria = Annotated[
    str, StringConstraints(strip_whitespace=True, min_length=1, max_length=100)
]


class CategoriaNueva(BaseModel):
    nombre: NombreCategoria


class CategoriaEdicion(BaseModel):
    nombre: NombreCategoria


class CategoriaSalida(BaseModel):
    id: uuid.UUID
    nombre: str


# Dinero y cantidades viajan como cadena decimal (E-01, RN-07). Nunca número JSON.
MontoCadena = Annotated[StrictStr, StringConstraints(pattern=r"^\d{1,14}(\.\d{1,4})?$")]
CantidadCadena = Annotated[StrictStr, StringConstraints(pattern=r"^\d{1,11}(\.\d{1,3})?$")]
Sku = Annotated[str, StringConstraints(strip_whitespace=True, min_length=1, max_length=64)]
NombreProducto = Annotated[
    str, StringConstraints(strip_whitespace=True, min_length=1, max_length=200)
]


CodigoBarras = Annotated[str, StringConstraints(strip_whitespace=True, min_length=1, max_length=64)]


class CodigoBarrasNuevo(BaseModel):
    codigo: CodigoBarras


class DineroEntrada(BaseModel):
    monto: MontoCadena
    moneda: Annotated[str, StringConstraints(pattern=r"^[A-Z]{3}$")]


class DineroSalida(BaseModel):
    monto: str
    moneda: str


class ProductoNuevo(BaseModel):
    nombre: NombreProducto
    unidad_codigo: Annotated[str, StringConstraints(min_length=1, max_length=16)]
    sku: Sku | None = None
    categoria_id: uuid.UUID | None = None
    costo_actual: DineroEntrada | None = None
    precio_venta: DineroEntrada | None = None
    stock_minimo: CantidadCadena | None = None
    codigos_barras: list[CodigoBarras] = []


class ProductoEdicion(BaseModel):
    """PATCH: solo cambia lo enviado; enviar `null` borra el valor (categoría, mínimo, costos)."""

    nombre: NombreProducto | None = None
    unidad_codigo: Annotated[str, StringConstraints(min_length=1, max_length=16)] | None = None
    sku: Sku | None = None
    categoria_id: uuid.UUID | None = None
    costo_actual: DineroEntrada | None = None
    precio_venta: DineroEntrada | None = None
    stock_minimo: CantidadCadena | None = None


class ImagenSalida(BaseModel):
    id: uuid.UUID
    url: str
    mime: str
    ancho: int
    alto: int
    bytes: int


class ProductoSalida(BaseModel):
    id: uuid.UUID
    sku: str
    nombre: str
    categoria: CategoriaSalida | None
    unidad: UnidadMedidaSalida
    costo_actual: DineroSalida | None
    precio_venta: DineroSalida | None
    stock_minimo: str | None
    stock_actual: str
    estado: Literal["activo", "archivado"]
    codigos_barras: list[str]
    imagen: ImagenSalida | None = None
