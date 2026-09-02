"""Errores de dominio. Sin ninguna noción de HTTP: la traducción vive en `app.api.errores`.

Cada error lleva un `code` estable en MAYUSCULA_SNAKE (para que el cliente decida), un
`message` para el dueño del negocio, en español y sin jerga, y `details` opcionales.
"""

from typing import Any


class ErrorDominio(Exception):
    """Base de todos los errores de negocio."""

    def __init__(self, code: str, message: str, details: dict[str, Any] | None = None) -> None:
        super().__init__(message)
        self.code = code
        self.message = message
        self.details: dict[str, Any] = details or {}


class NoEncontrado(ErrorDominio):
    """El recurso no existe dentro del negocio de la credencial (RF-AUT-007)."""


class Conflicto(ErrorDominio):
    """Una regla de negocio impide la operación: stock insuficiente, duplicado, estado."""


class ValidacionInvalida(ErrorDominio):
    """El dato es sintácticamente válido pero semánticamente no: cantidad con decimales
    en unidad discreta, factura que no cuadra."""


class NoAutenticado(ErrorDominio):
    """No hay credencial o la credencial no es válida."""


class SinPermiso(ErrorDominio):
    """Hay credencial pero no autoriza la acción."""
