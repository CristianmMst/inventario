"""Facturas de compra (RF-FAC-001..008, RN-18). Solo de compra: la v1 no emite ventas."""

import uuid
from datetime import date
from decimal import Decimal

from sqlalchemy.exc import IntegrityError
from sqlalchemy.ext.asyncio import AsyncSession

from app.dominio import errores as err
from app.dominio.tipos import Dinero, Moneda
from app.esquemas.catalogo import DineroEntrada, DineroSalida
from app.esquemas.compras import ProveedorBreve
from app.esquemas.facturas import (
    AnulacionFactura,
    FacturaEdicion,
    FacturaNueva,
    FacturaSalida,
    PaginaFacturas,
    PagoEntrada,
    RecepcionBreve,
    RecepcionesVinculacion,
)
from app.infra.paginacion import ParametrosPagina, decodificar_cursor, paginar
from app.modelos.facturas import Factura, FacturaRecepcion
from app.repositorios.compras import RepositorioRecepciones
from app.repositorios.facturas import RepositorioFacturas
from app.repositorios.identidad import RepositorioIdentidad
from app.servicios.imagenes import a_salida as imagen_salida
from app.servicios.proveedores import proveedor_seleccionable


def no_encontrada() -> err.NoEncontrado:
    return err.NoEncontrado("FACTURA_NO_ENCONTRADA", "Esa factura no existe.")


def _dinero(monto: Decimal, moneda: str) -> DineroSalida:
    d = Dinero(monto, Moneda(moneda)).a_api()
    return DineroSalida(monto=d["monto"], moneda=d["moneda"])


def a_salida(f: Factura, moneda_base: str) -> FacturaSalida:
    return FacturaSalida(
        id=f.id,
        proveedor=ProveedorBreve(id=f.proveedor.id, nombre=f.proveedor.nombre),
        numero=f.numero,
        fecha_emision=f.fecha_emision,
        fecha_vencimiento=f.fecha_vencimiento,
        moneda=f.moneda,
        tasa_cambio=str(f.tasa_cambio.quantize(Decimal("0.00000001"))),
        base_gravable=_dinero(f.base_gravable, f.moneda),
        impuesto=_dinero(f.impuesto, f.moneda),
        total=_dinero(f.total, f.moneda),
        total_base=_dinero(f.total_base, moneda_base),
        estado_pago=f.estado_pago,  # type: ignore[arg-type]
        fecha_pago=f.fecha_pago,
        motivo_anulacion=f.motivo_anulacion,
        notas=f.notas,
        recepciones=[
            RecepcionBreve(
                id=v.recepcion.id,
                numero=v.recepcion.numero,
                fecha=v.recepcion.fecha,
                total=_dinero(
                    sum(
                        (
                            linea.costo_unitario * linea.cantidad_recibida
                            for linea in v.recepcion.lineas
                        ),
                        Decimal(0),
                    ).quantize(Decimal("0.0001")),
                    v.recepcion.moneda,
                ),
            )
            for v in f.recepciones
        ],
        imagenes=[imagen_salida(fi.imagen) for fi in f.imagenes],
        created_at=f.created_at,
    )


def _monto(dinero: DineroEntrada, moneda: str, campo: str) -> Decimal:
    if dinero.moneda != moneda:
        raise err.ValidacionInvalida(
            "MONEDAS_DISTINTAS",
            f"El campo {campo} debe ir en {moneda}, la moneda de la factura.",
            {"campo": campo, "moneda": dinero.moneda, "moneda_factura": moneda},
        )
    return Dinero.desde_api(dinero.model_dump()).monto


def _tasa(moneda: str, moneda_base: str, tasa: str | None) -> Decimal:
    if moneda == moneda_base:
        return Decimal(1)
    if tasa is None:
        raise err.ValidacionInvalida(
            "TASA_OBLIGATORIA",
            f"La factura va en {moneda}: indica la tasa de cambio a {moneda_base}.",
            {"moneda": moneda, "moneda_base": moneda_base},
        )
    valor = Decimal(tasa)
    if valor <= 0:
        raise err.ValidacionInvalida("TASA_INVALIDA", "La tasa de cambio debe ser mayor que cero.")
    return valor


def _cuadrar(base: Decimal, impuesto: Decimal, total: Decimal, moneda: str) -> None:
    """RN-18: base + impuesto = total exactamente. El 422 muestra la diferencia."""
    suma = base + impuesto
    if suma != total:
        raise err.ValidacionInvalida(
            "FACTURA_NO_CUADRA",
            f"Base más impuesto suman {suma} y el total dice {total}; "
            f"hay una diferencia de {total - suma} {moneda}.",
            {
                "suma": str(suma.quantize(Decimal("0.0001"))),
                "total": str(total.quantize(Decimal("0.0001"))),
                "diferencia": str((total - suma).quantize(Decimal("0.0001"))),
                "moneda": moneda,
            },
        )


async def _cargar(sesion: AsyncSession, negocio_id: uuid.UUID, factura_id: uuid.UUID) -> Factura:
    factura = await RepositorioFacturas(sesion).por_id(negocio_id, factura_id)
    if factura is None:
        raise no_encontrada()
    return factura


async def _moneda_base(sesion: AsyncSession, negocio_id: uuid.UUID) -> str:
    return (await RepositorioIdentidad(sesion).negocio_por_id(negocio_id)).moneda_base


async def _salida_de(
    sesion: AsyncSession, negocio_id: uuid.UUID, factura_id: uuid.UUID
) -> FacturaSalida:
    return a_salida(
        await _cargar(sesion, negocio_id, factura_id), await _moneda_base(sesion, negocio_id)
    )


async def _vincular(
    sesion: AsyncSession, negocio_id: uuid.UUID, factura: Factura, recepcion_ids: list[uuid.UUID]
) -> None:
    """RF-FAC-006: recepciones del mismo proveedor; una recepción en una sola factura."""
    repo_facturas = RepositorioFacturas(sesion)
    repo_recepciones = RepositorioRecepciones(sesion)
    vinculos = []
    for rid in dict.fromkeys(recepcion_ids):
        recepcion = await repo_recepciones.por_id(negocio_id, rid)
        if recepcion is None:
            raise err.NoEncontrado("RECEPCION_NO_ENCONTRADA", "Esa recepción no existe.")
        if recepcion.proveedor_id != factura.proveedor_id:
            raise err.ValidacionInvalida(
                "RECEPCION_DE_OTRO_PROVEEDOR",
                f"La recepción {recepcion.numero} es de otro proveedor.",
                {"recepcion_id": str(rid)},
            )
        if recepcion.estado == "borrador":
            raise err.ValidacionInvalida(
                "RECEPCION_SIN_CONFIRMAR",
                f"La recepción {recepcion.numero} aún es un borrador; confírmala antes.",
                {"recepcion_id": str(rid)},
            )
        otra = await repo_facturas.recepcion_facturada_en(rid)
        if otra is not None and otra != factura.id:
            raise err.Conflicto(
                "RECEPCION_YA_FACTURADA",
                f"La recepción {recepcion.numero} ya está en otra factura.",
                {"recepcion_id": str(rid), "factura_id": str(otra)},
            )
        vinculos.append(FacturaRecepcion(recepcion_id=rid))
    factura.recepciones = vinculos


async def crear(sesion: AsyncSession, negocio_id: uuid.UUID, datos: FacturaNueva) -> FacturaSalida:
    repo = RepositorioFacturas(sesion)
    async with sesion.begin():
        proveedor = await proveedor_seleccionable(sesion, negocio_id, datos.proveedor_id)
        moneda_base = await _moneda_base(sesion, negocio_id)
        moneda = datos.moneda or moneda_base
        Moneda(moneda)
        base = _monto(datos.base_gravable, moneda, "base_gravable")
        impuesto = _monto(datos.impuesto, moneda, "impuesto")
        total = _monto(datos.total, moneda, "total")
        _cuadrar(base, impuesto, total, moneda)
        tasa = _tasa(moneda, moneda_base, datos.tasa_cambio)
        existente = await repo.por_numero(negocio_id, proveedor.id, datos.numero)
        if existente is not None:
            raise _duplicada(existente)
        factura = Factura(
            negocio_id=negocio_id,
            proveedor_id=proveedor.id,
            numero=datos.numero,
            fecha_emision=datos.fecha_emision,
            fecha_vencimiento=datos.fecha_vencimiento,
            moneda=moneda,
            base_gravable=base,
            impuesto=impuesto,
            total=total,
            tasa_cambio=tasa,
            total_base=(total * tasa).quantize(Decimal("0.0001")),
            notas=datos.notas,
        )
        repo.guardar(factura)
        try:
            await sesion.flush()
        except IntegrityError as e:
            raise err.Conflicto(
                "FACTURA_DUPLICADA",
                "Ese número ya existe para este proveedor.",
                {"numero": datos.numero},
            ) from e
        if datos.recepciones:
            await _vincular(sesion, negocio_id, factura, datos.recepciones)
            await sesion.flush()
        factura_id = factura.id
    async with sesion.begin():
        return await _salida_de(sesion, negocio_id, factura_id)


def _duplicada(existente: Factura) -> err.Conflicto:
    return err.Conflicto(
        "FACTURA_DUPLICADA",
        f"El número {existente.numero} ya está registrado para este proveedor.",
        {
            "numero": existente.numero,
            "factura_id": str(existente.id),
            "fecha_emision": existente.fecha_emision.isoformat(),
            "estado_pago": existente.estado_pago,
        },
    )


async def obtener(
    sesion: AsyncSession, negocio_id: uuid.UUID, factura_id: uuid.UUID
) -> FacturaSalida:
    async with sesion.begin():
        return await _salida_de(sesion, negocio_id, factura_id)


async def editar(
    sesion: AsyncSession, negocio_id: uuid.UUID, factura_id: uuid.UUID, datos: FacturaEdicion
) -> FacturaSalida:
    async with sesion.begin():
        factura = await _cargar(sesion, negocio_id, factura_id)
        enviados = datos.model_fields_set
        if "fecha_vencimiento" in enviados:
            factura.fecha_vencimiento = datos.fecha_vencimiento
        if "notas" in enviados:
            factura.notas = datos.notas
        await sesion.flush()
    async with sesion.begin():
        return await _salida_de(sesion, negocio_id, factura_id)


async def pagar(
    sesion: AsyncSession, negocio_id: uuid.UUID, factura_id: uuid.UUID, datos: PagoEntrada
) -> FacturaSalida:
    """RF-FAC-004: pendiente → pagada, con fecha de pago obligatoria."""
    async with sesion.begin():
        factura = await _cargar(sesion, negocio_id, factura_id)
        if factura.estado_pago == "pagada":
            raise err.Conflicto(
                "FACTURA_YA_PAGADA",
                f"La factura {factura.numero} ya está pagada desde {factura.fecha_pago}.",
                {"factura_id": str(factura.id), "fecha_pago": str(factura.fecha_pago)},
            )
        if factura.estado_pago == "anulada":
            raise err.Conflicto(
                "FACTURA_ANULADA",
                f"La factura {factura.numero} está anulada; no se puede pagar.",
                {"factura_id": str(factura.id)},
            )
        factura.estado_pago = "pagada"
        factura.fecha_pago = datos.fecha_pago
        await sesion.flush()
    async with sesion.begin():
        return await _salida_de(sesion, negocio_id, factura_id)


async def anular(
    sesion: AsyncSession, negocio_id: uuid.UUID, factura_id: uuid.UUID, datos: AnulacionFactura
) -> FacturaSalida:
    async with sesion.begin():
        factura = await _cargar(sesion, negocio_id, factura_id)
        if factura.estado_pago == "anulada":
            raise err.Conflicto(
                "FACTURA_ANULADA", f"La factura {factura.numero} ya está anulada.", {}
            )
        factura.estado_pago = "anulada"
        factura.motivo_anulacion = datos.motivo
        await sesion.flush()
    async with sesion.begin():
        return await _salida_de(sesion, negocio_id, factura_id)


async def vincular_recepciones(
    sesion: AsyncSession,
    negocio_id: uuid.UUID,
    factura_id: uuid.UUID,
    datos: RecepcionesVinculacion,
) -> FacturaSalida:
    async with sesion.begin():
        factura = await _cargar(sesion, negocio_id, factura_id)
        await _vincular(sesion, negocio_id, factura, datos.recepciones)
        await sesion.flush()
    async with sesion.begin():
        return await _salida_de(sesion, negocio_id, factura_id)


async def listar(
    sesion: AsyncSession,
    negocio_id: uuid.UUID,
    pagina: ParametrosPagina,
    *,
    proveedor_id: uuid.UUID | None = None,
    estado_pago: str | None = None,
    desde: date | None = None,
    hasta: date | None = None,
) -> PaginaFacturas:
    """RF-FAC-008: página más total acumulado del filtro aplicado, no de la página."""
    despues = None
    if pagina.cursor:
        c = decodificar_cursor(pagina.cursor)
        despues = (date.fromisoformat(str(c["f"])), uuid.UUID(str(c["id"])))
    async with sesion.begin():
        repo = RepositorioFacturas(sesion)
        filas = await repo.listar(
            negocio_id,
            proveedor_id=proveedor_id,
            estado_pago=estado_pago,
            desde=desde,
            hasta=hasta,
            limite=pagina.limit + 1,
            despues_de=despues,
        )
        total, cantidad = await repo.total_filtro(
            negocio_id, proveedor_id=proveedor_id, estado_pago=estado_pago, desde=desde, hasta=hasta
        )
        moneda_base = await _moneda_base(sesion, negocio_id)
        salidas = [a_salida(f, moneda_base) for f in filas]
    fechas = {f.id: f.fecha_emision.isoformat() for f in filas}
    pag = paginar(salidas, pagina.limit, clave_de=lambda s: {"f": fechas[s.id], "id": str(s.id)})
    return PaginaFacturas(
        datos=pag.datos,
        cursor_siguiente=pag.cursor_siguiente,
        tiene_mas=pag.tiene_mas,
        total_filtro=_dinero(total.quantize(Decimal("0.0001")), moneda_base),
        cantidad_filtro=cantidad,
    )
