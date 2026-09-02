"""RF-INT-002 y RF-INT-003: sobre común y payload de los 16 tipos de evento de spec.md §6."""

import uuid
from datetime import UTC, datetime
from decimal import Decimal

import pytest

from app.dominio import eventos as ev
from app.dominio.tipos import Cantidad, Dinero, Moneda

AUTOR = ev.Autor(tipo="usuario", id=uuid.uuid4(), nombre="Marta")
NEGOCIO = uuid.uuid4()
PRODUCTO = uuid.uuid4()
COP = Moneda("COP")

TIPOS_DE_LA_SPEC = {
    "producto.creado",
    "producto.actualizado",
    "producto.archivado",
    "movimiento.registrado",
    "movimiento.anulado",
    "stock.bajo_minimo",
    "stock.agotado",
    "stock.repuesto",
    "proveedor.creado",
    "compra.ordenada",
    "compra.recibida",
    "compra.recibida_parcial",
    "compra.cerrada_con_faltante",
    "factura.registrada",
    "factura.pagada",
    "inventario.discrepancia",
}


def test_rf_int_002_el_catalogo_tiene_exactamente_los_16_tipos_de_la_spec() -> None:
    assert set(ev.CATALOGO) == TIPOS_DE_LA_SPEC
    assert len(ev.CATALOGO) == 16


def test_rf_int_003_el_sobre_comun_lleva_id_tipo_version_negocio_momento_autor_y_payload() -> None:
    evento = ev.stock_agotado(
        NEGOCIO,
        AUTOR,
        producto_id=PRODUCTO,
        nombre="Cuaderno",
        stock_actual=Cantidad(Decimal(0)),
        unidad="unidad",
    )
    sobre = evento.a_dict()
    assert set(sobre) == {"id", "tipo", "version", "business_id", "ocurrido_en", "autor", "payload"}
    assert sobre["tipo"] == "stock.agotado" and sobre["version"] == 1
    assert sobre["business_id"] == str(NEGOCIO)
    assert sobre["autor"] == {"tipo": "usuario", "id": str(AUTOR.id), "nombre": "Marta"}
    assert datetime.fromisoformat(sobre["ocurrido_en"]).tzinfo is not None
    uuid.UUID(sobre["id"])


def test_rf_int_003_el_payload_es_json_puro_sin_decimal_ni_uuid() -> None:
    evento = ev.movimiento_registrado(
        NEGOCIO,
        AUTOR,
        movimiento_id=uuid.uuid4(),
        producto_id=PRODUCTO,
        tipo="salida",
        cantidad=Cantidad(Decimal("2.5")),
        motivo="venta",
        nota=None,
        forzado=False,
        stock_resultante=Cantidad(Decimal("7.5")),
        origen="app",
        recepcion_id=None,
    )
    payload = evento.a_dict()["payload"]
    assert payload["cantidad"] == "2.500" and payload["stock_resultante"] == "7.500"
    assert isinstance(payload["producto_id"], str)
    assert payload["recepcion_id"] is None
    import json

    json.dumps(evento.a_dict())


@pytest.mark.parametrize(
    ("tipo", "campos"),
    [
        (
            "producto.creado",
            {
                "producto_id",
                "nombre",
                "sku",
                "categoria",
                "unidad",
                "codigos_barras",
                "costo_actual",
                "precio_venta",
                "stock_minimo",
            },
        ),
        ("producto.actualizado", {"producto_id", "campos_cambiados"}),
        ("producto.archivado", {"producto_id", "nombre", "stock_al_archivar"}),
        (
            "movimiento.registrado",
            {
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
            },
        ),
        (
            "movimiento.anulado",
            {
                "movimiento_id_original",
                "contramovimiento_id",
                "producto_id",
                "cantidad",
                "motivo_anulacion",
                "stock_resultante",
            },
        ),
        (
            "stock.bajo_minimo",
            {
                "producto_id",
                "nombre",
                "stock_actual",
                "stock_minimo",
                "deficit",
                "unidad",
                "proveedor_habitual_id",
            },
        ),
        ("stock.agotado", {"producto_id", "nombre", "stock_actual", "unidad"}),
        ("stock.repuesto", {"producto_id", "stock_actual", "stock_minimo"}),
        ("proveedor.creado", {"proveedor_id", "nombre", "identificacion_fiscal", "contacto"}),
        (
            "compra.ordenada",
            {"orden_id", "proveedor_id", "fecha_esperada", "moneda", "total_estimado", "lineas"},
        ),
        (
            "compra.recibida",
            {
                "recepcion_id",
                "orden_id",
                "proveedor_id",
                "fecha",
                "moneda",
                "tasa_cambio",
                "total_moneda_base",
                "lineas",
                "movimientos_generados",
            },
        ),
        ("compra.recibida_parcial", {"orden_id", "recepcion_id", "lineas_pendientes"}),
        ("compra.cerrada_con_faltante", {"orden_id", "motivo", "lineas_faltantes"}),
        (
            "factura.registrada",
            {
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
            },
        ),
        ("factura.pagada", {"factura_id", "proveedor_id", "numero", "total", "fecha_pago"}),
        (
            "inventario.discrepancia",
            {
                "movimiento_id",
                "producto_id",
                "cantidad_solicitada",
                "stock_disponible",
                "stock_resultante",
                "motivo",
                "nota",
            },
        ),
    ],
)
def test_rf_int_002_cada_tipo_declara_los_campos_de_su_payload(tipo: str, campos: set[str]) -> None:
    assert set(ev.CATALOGO[tipo].campos) == campos
    assert ev.CATALOGO[tipo].version == 1


def test_rf_int_002_un_constructor_no_puede_omitir_ni_inventar_campos() -> None:
    with pytest.raises(ev.PayloadInvalido):
        ev.Evento.crear(NEGOCIO, AUTOR, "stock.agotado", {"producto_id": str(PRODUCTO)})
    with pytest.raises(ev.PayloadInvalido):
        ev.Evento.crear(
            NEGOCIO,
            AUTOR,
            "stock.agotado",
            {
                "producto_id": str(PRODUCTO),
                "nombre": "x",
                "stock_actual": "0.000",
                "unidad": "u",
                "extra": 1,
            },
        )
    with pytest.raises(ev.PayloadInvalido):
        ev.Evento.crear(NEGOCIO, AUTOR, "tipo.inexistente", {})


def test_rf_int_003_el_dinero_del_payload_viaja_como_cadena_y_moneda() -> None:
    evento = ev.factura_pagada(
        NEGOCIO,
        AUTOR,
        factura_id=uuid.uuid4(),
        proveedor_id=uuid.uuid4(),
        numero="F-1",
        total=Dinero(Decimal("119000"), COP),
        fecha_pago=datetime(2026, 9, 2, tzinfo=UTC).date(),
    )
    payload = evento.a_dict()["payload"]
    assert payload["total"] == {"monto": "119000.0000", "moneda": "COP"}
    assert payload["fecha_pago"] == "2026-09-02"


def test_rf_int_003_el_autor_de_servicio_lleva_el_nombre_de_la_credencial() -> None:
    servicio = ev.Autor(tipo="servicio", id=uuid.uuid4(), nombre="n8n")
    evento = ev.proveedor_creado(
        NEGOCIO,
        servicio,
        proveedor_id=uuid.uuid4(),
        nombre="P",
        identificacion_fiscal=None,
        contacto=None,
    )
    assert evento.a_dict()["autor"]["tipo"] == "servicio"
    assert evento.a_dict()["autor"]["nombre"] == "n8n"
