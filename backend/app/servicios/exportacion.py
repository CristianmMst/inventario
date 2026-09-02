"""RF-FAC-007: exportación de facturas por rango de fechas como un ZIP autocontenido con un
CSV y las imágenes del período, nombradas AAAA-MM-DD_proveedor_numero.jpg. Solo librería
estándar (zipfile, csv): el contador lo abre sin la app y sin conexión."""

import csv
import io
import re
import unicodedata
import uuid
import zipfile
from datetime import date
from decimal import Decimal

from sqlalchemy.ext.asyncio import AsyncSession

from app.almacenamiento import ImagenNoEncontrada, crear_almacen
from app.dominio import errores as err
from app.modelos.facturas import Factura
from app.repositorios.facturas import RepositorioFacturas
from app.repositorios.identidad import RepositorioIdentidad

COLUMNAS = [
    "numero",
    "proveedor",
    "identificacion_fiscal",
    "fecha_emision",
    "fecha_vencimiento",
    "moneda",
    "base_gravable",
    "impuesto",
    "total",
    "tasa_cambio",
    "total_moneda_base",
    "moneda_base",
    "estado_pago",
    "fecha_pago",
    "recepciones",
    "notas",
    "imagenes",
]

EXTENSIONES = {"image/jpeg": "jpg", "image/png": "png", "image/webp": "webp"}


def slug(texto: str, *, minusculas: bool = True) -> str:
    """Quita tildes y puntos, cambia lo que no sea letra o número por guiones."""
    sin_tildes = unicodedata.normalize("NFKD", texto).encode("ascii", "ignore").decode()
    sin_puntos = sin_tildes.replace(".", "")
    base = re.sub(r"[^A-Za-z0-9]+", "-", sin_puntos).strip("-")
    return base.lower() if minusculas else base


def nombre_imagen(factura: Factura, indice: int, mime: str) -> str:
    sufijo = "" if indice == 1 else f"_{indice}"
    extension = EXTENSIONES.get(mime, "jpg")
    return (
        f"{factura.fecha_emision.isoformat()}_{slug(factura.proveedor.nombre)}"
        f"_{slug(factura.numero, minusculas=False)}{sufijo}.{extension}"
    )


def _cuatro(valor: Decimal) -> str:
    return str(valor.quantize(Decimal("0.0001")))


async def exportar_facturas(
    sesion: AsyncSession, negocio_id: uuid.UUID, desde: date, hasta: date
) -> bytes:
    if desde > hasta:
        raise err.ValidacionInvalida(
            "RANGO_INVALIDO",
            "La fecha inicial no puede ser posterior a la final.",
            {"desde": desde.isoformat(), "hasta": hasta.isoformat()},
        )
    almacen = crear_almacen()
    salida = io.BytesIO()
    async with sesion.begin():
        moneda_base = (await RepositorioIdentidad(sesion).negocio_por_id(negocio_id)).moneda_base
        repo = RepositorioFacturas(sesion)
        facturas: list[Factura] = []
        cursor = None
        while True:
            pagina = await repo.listar(
                negocio_id, desde=desde, hasta=hasta, limite=501, despues_de=cursor
            )
            facturas.extend(pagina[:500])
            if len(pagina) <= 500:
                break
            ultima = pagina[499]
            cursor = (ultima.fecha_emision, ultima.id)
        facturas.sort(key=lambda f: (f.fecha_emision, f.created_at))

        with zipfile.ZipFile(salida, "w", compression=zipfile.ZIP_DEFLATED) as zf:
            texto = io.StringIO()
            escritor = csv.DictWriter(texto, fieldnames=COLUMNAS)
            escritor.writeheader()
            for factura in facturas:
                nombres = []
                for indice, adjunto in enumerate(factura.imagenes, start=1):
                    nombre = nombre_imagen(factura, indice, adjunto.imagen.mime)
                    try:
                        contenido = await almacen.leer(adjunto.imagen.clave_almacenamiento)
                    except ImagenNoEncontrada:
                        continue
                    zf.writestr(nombre, contenido)
                    nombres.append(nombre)
                escritor.writerow(
                    {
                        "numero": factura.numero,
                        "proveedor": factura.proveedor.nombre,
                        "identificacion_fiscal": factura.proveedor.identificacion_fiscal or "",
                        "fecha_emision": factura.fecha_emision.isoformat(),
                        "fecha_vencimiento": (
                            factura.fecha_vencimiento.isoformat()
                            if factura.fecha_vencimiento
                            else ""
                        ),
                        "moneda": factura.moneda,
                        "base_gravable": _cuatro(factura.base_gravable),
                        "impuesto": _cuatro(factura.impuesto),
                        "total": _cuatro(factura.total),
                        "tasa_cambio": str(factura.tasa_cambio.quantize(Decimal("0.00000001"))),
                        "total_moneda_base": _cuatro(factura.total_base),
                        "moneda_base": moneda_base,
                        "estado_pago": factura.estado_pago,
                        "fecha_pago": factura.fecha_pago.isoformat() if factura.fecha_pago else "",
                        "recepciones": ";".join(v.recepcion.numero for v in factura.recepciones),
                        "notas": factura.notas or "",
                        "imagenes": ";".join(nombres),
                    }
                )
            # BOM para que Excel abra el UTF-8 con tildes sin preguntar.
            zf.writestr("facturas.csv", "﻿" + texto.getvalue())
    return salida.getvalue()
