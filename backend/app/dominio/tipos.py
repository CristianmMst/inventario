"""Tipos de valor del dominio. Sin SQLAlchemy, sin Pydantic, sin FastAPI.

Dinero y cantidades son `Decimal` exacto: nunca coma flotante (constitution.md §2, E-01,
RN-07). En la API el dinero viaja como cadena decimal más ISO 4217 y la cantidad como cadena
decimal, para que ningún cliente los deserialice a `double`.
"""

from __future__ import annotations

import re
from dataclasses import dataclass
from decimal import Decimal, InvalidOperation
from enum import StrEnum
from typing import Any

from app.dominio import errores as err

DECIMALES_DINERO = 4  # NUMERIC(18,4)
DECIMALES_CANTIDAD = 3  # NUMERIC(14,3)

_ISO_4217 = re.compile(r"^[A-Z]{3}$")


def _rechazar_float(valor: object, nombre: str) -> None:
    if isinstance(valor, float):
        raise TypeError(f"{nombre} no acepta float: usa Decimal o cadena decimal")


def _decimales_de(valor: Decimal) -> int:
    exponente = valor.normalize().as_tuple().exponent
    return -exponente if isinstance(exponente, int) and exponente < 0 else 0


def _a_decimal(valor: str | int | Decimal, code: str, mensaje: str) -> Decimal:
    try:
        return Decimal(valor)
    except (InvalidOperation, ValueError, TypeError) as e:
        raise err.ValidacionInvalida(code, mensaje, {"valor": str(valor)}) from e


@dataclass(frozen=True, slots=True)
class Moneda:
    codigo: str

    def __post_init__(self) -> None:
        if not isinstance(self.codigo, str) or not _ISO_4217.match(self.codigo):
            raise err.ValidacionInvalida(
                "MONEDA_INVALIDA",
                "La moneda debe ser un código ISO 4217 de tres letras mayúsculas, como COP.",
                {"moneda": str(self.codigo)},
            )

    def __str__(self) -> str:
        return self.codigo


@dataclass(frozen=True, slots=True)
class Cantidad:
    """Cantidad de stock. Puede ser cero o negativa: el signo lo controla quien la use
    (un stock puede quedar negativo si se fuerza, RN-04). Máximo 3 decimales."""

    valor: Decimal

    def __post_init__(self) -> None:
        _rechazar_float(self.valor, "Cantidad")
        if not isinstance(self.valor, Decimal):
            raise TypeError("Cantidad exige Decimal")
        if not self.valor.is_finite() or _decimales_de(self.valor) > DECIMALES_CANTIDAD:
            raise err.ValidacionInvalida(
                "CANTIDAD_INVALIDA",
                f"La cantidad admite como mucho {DECIMALES_CANTIDAD} decimales.",
                {"cantidad": str(self.valor)},
            )
        object.__setattr__(self, "valor", self.valor.quantize(Decimal(10) ** -DECIMALES_CANTIDAD))

    @classmethod
    def desde_api(cls, valor: str | int) -> Cantidad:
        _rechazar_float(valor, "Cantidad")
        return cls(_a_decimal(valor, "CANTIDAD_INVALIDA", "La cantidad no es un número válido."))

    def a_api(self) -> str:
        return str(self.valor)

    def validar_para(self, unidad: UnidadMedida) -> None:
        """RN-07 / RF-INV-009: discreta exige entero; continua respeta sus decimales."""
        if _decimales_de(self.valor) > unidad.decimales:
            mensaje = (
                f"{unidad.nombre} se cuenta por unidades enteras."
                if unidad.tipo is TipoUnidad.DISCRETA
                else f"{unidad.nombre} admite como mucho {unidad.decimales} decimales."
            )
            raise err.ValidacionInvalida(
                "CANTIDAD_INVALIDA_PARA_UNIDAD",
                mensaje,
                {"cantidad": self.a_api(), "unidad": unidad.codigo, "decimales": unidad.decimales},
            )

    def es_positiva(self) -> bool:
        return self.valor > 0

    def __add__(self, otra: Cantidad) -> Cantidad:
        return Cantidad(self.valor + otra.valor)

    def __sub__(self, otra: Cantidad) -> Cantidad:
        return Cantidad(self.valor - otra.valor)

    def __neg__(self) -> Cantidad:
        return Cantidad(-self.valor)

    def __lt__(self, otra: Cantidad) -> bool:
        return self.valor < otra.valor

    def __le__(self, otra: Cantidad) -> bool:
        return self.valor <= otra.valor


@dataclass(frozen=True, slots=True)
class Dinero:
    monto: Decimal
    moneda: Moneda

    def __post_init__(self) -> None:
        _rechazar_float(self.monto, "Dinero")
        if not isinstance(self.monto, Decimal):
            raise TypeError("Dinero exige Decimal")
        if not self.monto.is_finite() or _decimales_de(self.monto) > DECIMALES_DINERO:
            raise err.ValidacionInvalida(
                "MONTO_INVALIDO",
                f"El monto admite como mucho {DECIMALES_DINERO} decimales.",
                {"monto": str(self.monto)},
            )
        object.__setattr__(self, "monto", self.monto.quantize(Decimal(10) ** -DECIMALES_DINERO))

    @classmethod
    def desde_api(cls, datos: dict[str, Any]) -> Dinero:
        monto = datos.get("monto")
        _rechazar_float(monto, "Dinero")
        if not isinstance(monto, str | int):
            raise err.ValidacionInvalida(
                "MONTO_INVALIDO", "El monto debe ser una cadena decimal.", {"monto": str(monto)}
            )
        return cls(
            _a_decimal(monto, "MONTO_INVALIDO", "El monto no es un número válido."),
            Moneda(str(datos.get("moneda", ""))),
        )

    def a_api(self) -> dict[str, str]:
        return {"monto": str(self.monto), "moneda": self.moneda.codigo}

    def _misma_moneda(self, otro: Dinero) -> None:
        if self.moneda != otro.moneda:
            raise err.ValidacionInvalida(
                "MONEDAS_DISTINTAS",
                "No se pueden operar montos en monedas distintas sin una tasa de cambio.",
                {"monedas": [self.moneda.codigo, otro.moneda.codigo]},
            )

    def __add__(self, otro: Dinero) -> Dinero:
        self._misma_moneda(otro)
        return Dinero(self.monto + otro.monto, self.moneda)

    def __sub__(self, otro: Dinero) -> Dinero:
        self._misma_moneda(otro)
        return Dinero(self.monto - otro.monto, self.moneda)

    def __mul__(self, cantidad: Cantidad) -> Dinero:
        producto = self.monto * cantidad.valor
        return Dinero(producto.quantize(Decimal(10) ** -DECIMALES_DINERO), self.moneda)

    def convertir(self, tasa: Decimal, a: Moneda) -> Dinero:
        """Equivalente en otra moneda con una tasa dada (RN-10). Redondea a 4 decimales."""
        _rechazar_float(tasa, "tasa")
        return Dinero((self.monto * tasa).quantize(Decimal(10) ** -DECIMALES_DINERO), a)


class TipoUnidad(StrEnum):
    DISCRETA = "discreta"
    CONTINUA = "continua"


@dataclass(frozen=True, slots=True)
class UnidadMedida:
    """RF-CAT-004: la unidad declara si es discreta o continua y cuántos decimales admite."""

    codigo: str
    nombre: str
    tipo: TipoUnidad
    decimales: int

    def __post_init__(self) -> None:
        if self.tipo is TipoUnidad.DISCRETA and self.decimales != 0:
            raise err.ValidacionInvalida(
                "UNIDAD_INVALIDA",
                "Una unidad discreta no admite decimales.",
                {"unidad": self.codigo, "decimales": self.decimales},
            )
        if not 0 <= self.decimales <= DECIMALES_CANTIDAD:
            raise err.ValidacionInvalida(
                "UNIDAD_INVALIDA",
                f"Los decimales de una unidad van de 0 a {DECIMALES_CANTIDAD}.",
                {"unidad": self.codigo, "decimales": self.decimales},
            )
