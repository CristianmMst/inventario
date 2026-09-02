"""Reglas de movimientos de inventario. Sin SQLAlchemy ni FastAPI.

RF-INV-001: cinco tipos; el tipo determina el signo, nunca la cantidad.
RF-INV-009 / RN-07: la cantidad es positiva y respeta la unidad.
RN-16: la merma es un tipo propio, distinto de la salida.
RN-15: el ajuste lleva dirección explícita, porque el servidor calcula el delta del conteo.
"""

from __future__ import annotations

from enum import StrEnum
from typing import Literal

from app.dominio import errores as err
from app.dominio.tipos import Cantidad, UnidadMedida

Direccion = Literal[-1, 1]


class TipoMovimiento(StrEnum):
    ENTRADA = "entrada"
    SALIDA = "salida"
    AJUSTE = "ajuste"
    MERMA = "merma"
    CONTRAMOVIMIENTO = "contramovimiento"

    @property
    def resta_stock(self) -> bool:
        """Salida y merma siempre restan (RN-03 se aplica a ambas)."""
        return self in (TipoMovimiento.SALIDA, TipoMovimiento.MERMA)

    @property
    def signo_fijo(self) -> bool:
        return self in (TipoMovimiento.ENTRADA, TipoMovimiento.SALIDA, TipoMovimiento.MERMA)


def signo_de(tipo: TipoMovimiento) -> Direccion:
    """Signo de los tipos con dirección fija. Ajuste y contramovimiento la traen consigo."""
    if tipo is TipoMovimiento.ENTRADA:
        return 1
    if tipo.resta_stock:
        return -1
    raise ValueError(f"el tipo {tipo.value} no tiene signo fijo: indica la dirección")


def validar_cantidad_movimiento(cantidad: Cantidad, unidad: UnidadMedida) -> None:
    """RF-INV-009: mayor que cero; entera en unidad discreta; hasta 3 decimales en continua."""
    if not cantidad.es_positiva():
        raise err.ValidacionInvalida(
            "CANTIDAD_NO_POSITIVA",
            "La cantidad debe ser mayor que cero. El sentido lo da el tipo de movimiento.",
            {"cantidad": cantidad.a_api()},
        )
    cantidad.validar_para(unidad)


def stock_resultante(
    stock: Cantidad,
    tipo: TipoMovimiento,
    cantidad: Cantidad,
    *,
    direccion: Direccion | None = None,
) -> Cantidad:
    """Stock tras aplicar el movimiento. Para ajuste y contramovimiento la dirección es
    obligatoria; para el resto se ignora porque la fija el tipo."""
    if tipo.signo_fijo:
        signo = signo_de(tipo)
    elif direccion is None:
        raise ValueError(f"el tipo {tipo.value} exige dirección")
    else:
        signo = direccion
    return stock + cantidad if signo == 1 else stock - cantidad
