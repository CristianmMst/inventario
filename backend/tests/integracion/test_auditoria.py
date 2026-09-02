"""RNF-13 / RNF-16 (T-099): auditoría y retención.

Ningún proceso borra registros de auditoría y nada se depura automáticamente en v1:
- la API no publica DELETE sobre movimientos, eventos, recepciones ni facturas;
- el código de la aplicación no ejecuta borrados sobre esas tablas;
- la base rechaza DELETE en `movimientos` aunque alguien lo intente con la conexión de la app;
- todo movimiento, recepción, factura y evento lleva autor o marca de tiempo no nulos;
- no hay planificadores ni tareas periódicas de limpieza en el proyecto.
"""

from __future__ import annotations

import re
import tokenize
from pathlib import Path

import sqlalchemy as sa

from app.main import app

RAIZ_APP = Path(__file__).resolve().parents[2] / "app"
TABLAS_AUDITORIA = (
    "movimientos",
    "eventos",
    "recepciones",
    "recepciones_lineas",
    "facturas",
    "facturas_imagenes",
)


def test_rnf_13_la_api_no_publica_borrado_de_registros_de_auditoria() -> None:
    documento = app.openapi()
    borrados = [
        ruta
        for ruta, item in documento["paths"].items()
        if "delete" in item
        and any(
            f"/{tabla.replace('_', '-')}" in ruta
            for tabla in ("movimientos", "eventos", "recepciones", "facturas")
        )
    ]
    # El único DELETE bajo /facturas quita una imagen adjunta de una factura; la factura y sus
    # datos permanecen. Ninguna otra ruta de auditoría admite DELETE.
    assert borrados == ["/api/v1/facturas/{factura_id}/imagenes/{imagen_id}"], borrados


def test_rnf_13_el_codigo_de_la_app_no_borra_tablas_de_auditoria() -> None:
    patron_sql = re.compile(
        r"delete\s+from\s+(" + "|".join(TABLAS_AUDITORIA) + r")\b", re.IGNORECASE
    )
    patron_orm = re.compile(r"sa\.delete\((Movimiento|Evento|Recepcion|RecepcionLinea|Factura)\b")
    hallazgos = []
    for archivo in RAIZ_APP.rglob("*.py"):
        texto = archivo.read_text(encoding="utf-8")
        if (
            patron_sql.search(texto)
            or patron_orm.search(texto)
            or re.search(r"\.delete\((movimiento|evento|recepcion|factura)\b", texto)
        ):
            hallazgos.append(str(archivo.relative_to(RAIZ_APP)))
    assert not hallazgos, f"código que borra auditoría: {hallazgos}"


def test_rnf_16_no_hay_depuracion_automatica_en_el_proyecto() -> None:
    """Se revisa el código (no los comentarios ni las cadenas): sin planificadores ni purgas."""
    sospechosos = re.compile(
        r"^(apscheduler|celery|BackgroundTasks|schedule|cron|crontab|purgar|depurar|retencion)$",
        re.IGNORECASE,
    )
    hallazgos = []
    for archivo in RAIZ_APP.rglob("*.py"):
        with archivo.open("rb") as f:
            for token in tokenize.tokenize(f.readline):
                if token.type == tokenize.NAME and sospechosos.match(token.string):
                    hallazgos.append(
                        f"{archivo.relative_to(RAIZ_APP)}:{token.start[0]}: {token.string}"
                    )
    assert not hallazgos, "posibles tareas de depuración:\n" + "\n".join(hallazgos)


async def test_rnf_13_la_base_rechaza_borrar_movimientos(sesion) -> None:  # noqa: ANN001
    """El trigger es por fila: se comprueba su definición (UPDATE y DELETE) y que está activo."""
    async with sesion.begin():
        fila = (
            await sesion.execute(
                sa.text(
                    "SELECT tgenabled, pg_get_triggerdef(oid) FROM pg_trigger "
                    "WHERE tgrelid = 'movimientos'::regclass AND tgname = 'movimientos_inmutables'"
                )
            )
        ).first()
    assert fila is not None, "falta el trigger movimientos_inmutables (RN-02)"
    assert fila[0] != "D", "el trigger está deshabilitado"
    definicion = fila[1].upper()
    assert "BEFORE" in definicion and "DELETE" in definicion and "UPDATE" in definicion, definicion


async def test_rnf_13_autor_y_momento_son_obligatorios_en_las_tablas_de_auditoria(sesion) -> None:  # noqa: ANN001
    esperadas = {
        "movimientos": {"autor_tipo", "autor_id", "ocurrido_en", "created_at"},
        "eventos": {"autor_tipo", "autor_id", "autor_nombre", "ocurrido_en", "created_at"},
        "recepciones": {"created_at"},
        "facturas": {"created_at"},
    }
    async with sesion.begin():
        filas = (
            await sesion.execute(
                sa.text(
                    "SELECT table_name, column_name FROM information_schema.columns "
                    "WHERE table_schema = 'public' AND is_nullable = 'NO' "
                    "AND table_name IN ('movimientos', 'eventos', 'recepciones', 'facturas')"
                )
            )
        ).all()
    no_nulas = {}
    for tabla, columna in filas:
        no_nulas.setdefault(tabla, set()).add(columna)
    for tabla, columnas in esperadas.items():
        faltan = columnas - no_nulas.get(tabla, set())
        assert not faltan, f"{tabla}: columnas de auditoría que admiten NULL: {faltan}"
