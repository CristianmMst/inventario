"""Catálogo de eventos de dominio y sobre común (spec.md §6; RF-INT-002, RF-INT-003).

Este módulo es el único sitio donde se decide qué campos lleva cada tipo. Añadir un tipo no
rompe compatibilidad; cambiar el significado o quitar un campo exige subir la `version`.
Sin SQLAlchemy ni FastAPI: solo construye el sobre. Persistirlo es cosa del servicio (RN-21).
"""

from __future__ import annotations

import uuid
from dataclasses import dataclass
from datetime import UTC, date, datetime
from decimal import Decimal
from typing import Any, Literal

from app.dominio.tipos import Cantidad, Dinero


class PayloadInvalido(Exception):
    """El payload no coincide con los campos declarados para el tipo."""


@dataclass(frozen=True, slots=True)
class Autor:
    tipo: Literal["usuario", "servicio"]
    id: uuid.UUID
    nombre: str


@dataclass(frozen=True, slots=True)
class Definicion:
    tipo: str
    version: int
    campos: tuple[str, ...]


def _def(tipo: str, *campos: str) -> Definicion:
    return Definicion(tipo, 1, campos)


CATALOGO: dict[str, Definicion] = {
    d.tipo: d
    for d in (
        _def(
            "producto.creado",
            "producto_id",
            "nombre",
            "sku",
            "categoria",
            "unidad",
            "codigos_barras",
            "costo_actual",
            "precio_venta",
            "stock_minimo",
        ),
        _def("producto.actualizado", "producto_id", "campos_cambiados"),
        _def("producto.archivado", "producto_id", "nombre", "stock_al_archivar"),
        _def(
            "movimiento.registrado",
            "movimiento_id",
            "producto_id",
            "tipo",
            "cantidad",
            "motivo",
            "nota",
            "forzado",
            "stock_resultante",
            "origen",
            "recepcion_id",
        ),
        _def(
            "movimiento.anulado",
            "movimiento_id_original",
            "contramovimiento_id",
            "producto_id",
            "cantidad",
            "motivo_anulacion",
            "stock_resultante",
        ),
        _def(
            "stock.bajo_minimo",
            "producto_id",
            "nombre",
            "stock_actual",
            "stock_minimo",
            "deficit",
            "unidad",
            "proveedor_habitual_id",
        ),
        _def("stock.agotado", "producto_id", "nombre", "stock_actual", "unidad"),
        _def("stock.repuesto", "producto_id", "stock_actual", "stock_minimo"),
        _def("proveedor.creado", "proveedor_id", "nombre", "identificacion_fiscal", "contacto"),
        _def(
            "compra.ordenada",
            "orden_id",
            "proveedor_id",
            "fecha_esperada",
            "moneda",
            "total_estimado",
            "lineas",
        ),
        _def(
            "compra.recibida",
            "recepcion_id",
            "orden_id",
            "proveedor_id",
            "fecha",
            "moneda",
            "tasa_cambio",
            "total_moneda_base",
            "lineas",
            "movimientos_generados",
        ),
        _def("compra.recibida_parcial", "orden_id", "recepcion_id", "lineas_pendientes"),
        _def("compra.cerrada_con_faltante", "orden_id", "motivo", "lineas_faltantes"),
        _def(
            "factura.registrada",
            "factura_id",
            "proveedor_id",
            "numero",
            "fecha_emision",
            "fecha_vencimiento",
            "moneda",
            "base_gravable",
            "impuesto",
            "total",
            "total_moneda_base",
            "estado_pago",
            "recepciones",
            "imagenes",
        ),
        _def("factura.pagada", "factura_id", "proveedor_id", "numero", "total", "fecha_pago"),
        _def(
            "inventario.discrepancia",
            "movimiento_id",
            "producto_id",
            "cantidad_solicitada",
            "stock_disponible",
            "stock_resultante",
            "motivo",
            "nota",
        ),
    )
}


def json_puro(valor: Any) -> Any:
    """Convierte tipos del dominio a JSON: Cantidad y Decimal a cadena, Dinero a
    {monto, moneda}, UUID a cadena, fechas a ISO 8601."""
    if valor is None or isinstance(valor, bool | int | str):
        return valor
    if isinstance(valor, Cantidad):
        return valor.a_api()
    if isinstance(valor, Dinero):
        return valor.a_api()
    if isinstance(valor, Decimal):
        return str(valor)
    if isinstance(valor, uuid.UUID):
        return str(valor)
    if isinstance(valor, datetime):
        return valor.isoformat()
    if isinstance(valor, date):
        return valor.isoformat()
    if isinstance(valor, dict):
        return {str(k): json_puro(v) for k, v in valor.items()}
    if isinstance(valor, list | tuple):
        return [json_puro(v) for v in valor]
    if isinstance(valor, float):
        raise PayloadInvalido("un payload nunca lleva coma flotante")
    raise PayloadInvalido(f"tipo no serializable en un evento: {type(valor).__name__}")


@dataclass(frozen=True, slots=True)
class Evento:
    id: uuid.UUID
    tipo: str
    version: int
    business_id: uuid.UUID
    ocurrido_en: datetime
    autor: Autor
    payload: dict[str, Any]

    @classmethod
    def crear(
        cls,
        negocio_id: uuid.UUID,
        autor: Autor,
        tipo: str,
        payload: dict[str, Any],
        *,
        ocurrido_en: datetime | None = None,
    ) -> Evento:
        definicion = CATALOGO.get(tipo)
        if definicion is None:
            raise PayloadInvalido(f"tipo de evento desconocido: {tipo}")
        esperados, recibidos = set(definicion.campos), set(payload)
        if esperados != recibidos:
            faltan, sobran = esperados - recibidos, recibidos - esperados
            raise PayloadInvalido(
                f"payload de {tipo}: faltan {sorted(faltan)} y sobran {sorted(sobran)}"
            )
        return cls(
            id=uuid.uuid4(),
            tipo=tipo,
            version=definicion.version,
            business_id=negocio_id,
            ocurrido_en=ocurrido_en or datetime.now(UTC),
            autor=autor,
            payload={k: json_puro(v) for k, v in payload.items()},
        )

    def a_dict(self) -> dict[str, Any]:
        return {
            "id": str(self.id),
            "tipo": self.tipo,
            "version": self.version,
            "business_id": str(self.business_id),
            "ocurrido_en": self.ocurrido_en.isoformat(),
            "autor": {
                "tipo": self.autor.tipo,
                "id": str(self.autor.id),
                "nombre": self.autor.nombre,
            },
            "payload": self.payload,
        }


# Constructores por tipo: cada uno nombra sus campos para que el llamador no pueda equivocarse.


def producto_creado(negocio_id: uuid.UUID, autor: Autor, **campos: Any) -> Evento:
    return Evento.crear(negocio_id, autor, "producto.creado", campos)


def producto_actualizado(negocio_id: uuid.UUID, autor: Autor, **campos: Any) -> Evento:
    return Evento.crear(negocio_id, autor, "producto.actualizado", campos)


def producto_archivado(negocio_id: uuid.UUID, autor: Autor, **campos: Any) -> Evento:
    return Evento.crear(negocio_id, autor, "producto.archivado", campos)


def movimiento_registrado(negocio_id: uuid.UUID, autor: Autor, **campos: Any) -> Evento:
    return Evento.crear(negocio_id, autor, "movimiento.registrado", campos)


def movimiento_anulado(negocio_id: uuid.UUID, autor: Autor, **campos: Any) -> Evento:
    return Evento.crear(negocio_id, autor, "movimiento.anulado", campos)


def stock_bajo_minimo(negocio_id: uuid.UUID, autor: Autor, **campos: Any) -> Evento:
    return Evento.crear(negocio_id, autor, "stock.bajo_minimo", campos)


def stock_agotado(negocio_id: uuid.UUID, autor: Autor, **campos: Any) -> Evento:
    return Evento.crear(negocio_id, autor, "stock.agotado", campos)


def stock_repuesto(negocio_id: uuid.UUID, autor: Autor, **campos: Any) -> Evento:
    return Evento.crear(negocio_id, autor, "stock.repuesto", campos)


def proveedor_creado(negocio_id: uuid.UUID, autor: Autor, **campos: Any) -> Evento:
    return Evento.crear(negocio_id, autor, "proveedor.creado", campos)


def compra_ordenada(negocio_id: uuid.UUID, autor: Autor, **campos: Any) -> Evento:
    return Evento.crear(negocio_id, autor, "compra.ordenada", campos)


def compra_recibida(negocio_id: uuid.UUID, autor: Autor, **campos: Any) -> Evento:
    return Evento.crear(negocio_id, autor, "compra.recibida", campos)


def compra_recibida_parcial(negocio_id: uuid.UUID, autor: Autor, **campos: Any) -> Evento:
    return Evento.crear(negocio_id, autor, "compra.recibida_parcial", campos)


def compra_cerrada_con_faltante(negocio_id: uuid.UUID, autor: Autor, **campos: Any) -> Evento:
    return Evento.crear(negocio_id, autor, "compra.cerrada_con_faltante", campos)


def factura_registrada(negocio_id: uuid.UUID, autor: Autor, **campos: Any) -> Evento:
    return Evento.crear(negocio_id, autor, "factura.registrada", campos)


def factura_pagada(negocio_id: uuid.UUID, autor: Autor, **campos: Any) -> Evento:
    return Evento.crear(negocio_id, autor, "factura.pagada", campos)


def inventario_discrepancia(negocio_id: uuid.UUID, autor: Autor, **campos: Any) -> Evento:
    return Evento.crear(negocio_id, autor, "inventario.discrepancia", campos)
