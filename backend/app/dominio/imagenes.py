"""RNF-05: límites de las imágenes, verificados en el servidor. La compresión la hace el
celular; aquí solo se comprueba y se rechaza lo que exceda."""

from __future__ import annotations

import hashlib
import io
from dataclasses import dataclass
from enum import StrEnum

from PIL import Image, UnidentifiedImageError

from app.dominio import errores as err


class TipoImagen(StrEnum):
    PRODUCTO = "producto"
    FACTURA = "factura"


# (bytes máximos, lado mayor máximo en píxeles)
LIMITES: dict[TipoImagen, tuple[int, int]] = {
    TipoImagen.PRODUCTO: (300 * 1024, 1280),
    TipoImagen.FACTURA: (1536 * 1024, 2048),
}

MIMES_ADMITIDOS = {"JPEG": "image/jpeg", "PNG": "image/png", "WEBP": "image/webp"}


@dataclass(frozen=True, slots=True)
class InfoImagen:
    mime: str
    ancho: int
    alto: int
    bytes: int
    checksum: str


def validar_imagen(contenido: bytes, tipo: TipoImagen) -> InfoImagen:
    """Inspecciona el contenido real, no el mime declarado. Rechaza lo que no sea imagen, lo
    que pese más o mida más de lo que RNF-05 permite para ese tipo."""
    max_bytes, max_lado = LIMITES[tipo]
    if len(contenido) > max_bytes:
        raise err.ValidacionInvalida(
            "IMAGEN_DEMASIADO_PESADA",
            f"La imagen pesa {len(contenido) // 1024} KB y el máximo para {tipo.value} es "
            f"{max_bytes // 1024} KB. Comprímela antes de subirla.",
            {"bytes": len(contenido), "maximo_bytes": max_bytes, "tipo": tipo.value},
        )
    try:
        with Image.open(io.BytesIO(contenido)) as imagen:
            formato = imagen.format or ""
            ancho, alto = imagen.size
    except (UnidentifiedImageError, OSError, ValueError) as e:
        raise err.ValidacionInvalida(
            "IMAGEN_INVALIDA", "El archivo no es una imagen que podamos leer."
        ) from e
    mime = MIMES_ADMITIDOS.get(formato)
    if mime is None:
        raise err.ValidacionInvalida(
            "FORMATO_NO_ADMITIDO",
            "Solo se admiten imágenes JPEG, PNG o WebP.",
            {"formato": formato},
        )
    lado_mayor = max(ancho, alto)
    if lado_mayor > max_lado:
        raise err.ValidacionInvalida(
            "IMAGEN_DEMASIADO_GRANDE",
            f"La imagen mide {ancho}×{alto} px y el lado mayor no puede pasar de {max_lado} px.",
            {"ancho": ancho, "alto": alto, "lado_mayor": lado_mayor, "maximo_px": max_lado},
        )
    return InfoImagen(
        mime=mime,
        ancho=ancho,
        alto=alto,
        bytes=len(contenido),
        checksum=hashlib.sha256(contenido).hexdigest(),
    )
