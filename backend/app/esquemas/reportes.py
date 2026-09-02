import uuid
from datetime import datetime
from typing import Generic, TypeVar

from pydantic import BaseModel

from app.esquemas.catalogo import DineroSalida
from app.esquemas.compras import ProductoBreve, ProveedorBreve

T = TypeVar("T")


class Lista(BaseModel, Generic[T]):  # noqa: UP046  (Pydantic y PEP 695)
    datos: list[T]
    cursor_siguiente: str | None
    tiene_mas: bool


class CategoriaBreve(BaseModel):
    id: uuid.UUID
    nombre: str


class FilaBajoMinimo(BaseModel):
    producto: ProductoBreve
    stock_actual: str
    stock_minimo: str
    deficit: str
    deficit_relativo: str


class FilaAgotado(BaseModel):
    producto: ProductoBreve
    stock_actual: str
    stock_minimo: str | None


class FilaSinMovimiento(BaseModel):
    producto: ProductoBreve
    stock_actual: str
    valor_a_costo: DineroSalida | None
    ultimo_movimiento_en: datetime | None
    creado_en: datetime


class ValorCategoria(BaseModel):
    categoria: CategoriaBreve | None
    productos: int
    valor: DineroSalida


class FilaNoValorizable(BaseModel):
    producto: ProductoBreve
    stock_actual: str


class Valorizacion(BaseModel):
    """RF-REP-003: stock × costo actual en moneda base (RN-09). Sin parámetro de fecha."""

    total: DineroSalida
    productos_valorizados: int
    por_categoria: list[ValorCategoria]
    no_valorizables: Lista[FilaNoValorizable]


class ComprasProveedor(BaseModel):
    proveedor: ProveedorBreve
    total_recibido: DineroSalida
    total_facturado: DineroSalida


class ComprasCategoria(BaseModel):
    categoria: CategoriaBreve | None
    total_recibido: DineroSalida


class ResumenCompras(BaseModel):
    """RF-REP-005: en moneda base con las tasas congeladas en cada documento."""

    desde: str
    hasta: str
    total_recibido: DineroSalida
    total_facturado: DineroSalida
    recepciones: int
    facturas: int
    por_proveedor: list[ComprasProveedor]
    por_categoria: list[ComprasCategoria]


class MermaMotivo(BaseModel):
    motivo: str
    etiqueta: str
    cantidad: str
    valor: DineroSalida


class MermaProducto(BaseModel):
    producto: ProductoBreve
    cantidad: str
    valor: DineroSalida


class ResumenMermas(BaseModel):
    """RF-REP-006 / RN-16: solo tipo merma, sin anuladas, valorizadas al costo actual."""

    desde: str
    hasta: str
    total_cantidad: str
    total_valor: DineroSalida
    por_motivo: list[MermaMotivo]
    por_producto: Lista[MermaProducto]


class FilaDiscrepancia(BaseModel):
    movimiento_id: uuid.UUID
    producto: ProductoBreve
    tipo: str
    cantidad: str
    stock_resultante: str
    motivo: str
    nota: str | None
    ocurrido_en: datetime
    autor_tipo: str
