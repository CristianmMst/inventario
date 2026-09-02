"""Órdenes de compra (RF-COM-002, RF-COM-003, RF-COM-010). Planificación opcional (RN-11)."""

import uuid
from datetime import UTC, datetime
from decimal import Decimal

from sqlalchemy.ext.asyncio import AsyncSession

from app.dominio import errores as err
from app.dominio.movimientos import validar_cantidad_movimiento
from app.dominio.tipos import Cantidad, Dinero, Moneda
from app.esquemas.catalogo import DineroEntrada, DineroSalida
from app.esquemas.compras import (
    CancelacionEntrada,
    CierreFaltanteEntrada,
    LineaOrdenEntrada,
    LineaOrdenSalida,
    OrdenEdicion,
    OrdenNueva,
    OrdenSalida,
    ProductoBreve,
    ProveedorBreve,
)
from app.infra.paginacion import Pagina, ParametrosPagina, decodificar_cursor, paginar
from app.modelos.compras import OrdenCompra, OrdenCompraLinea
from app.repositorios.compras import RepositorioOrdenes
from app.repositorios.identidad import RepositorioIdentidad
from app.servicios.movimientos import producto_operable, unidad_de
from app.servicios.proveedores import proveedor_seleccionable


def no_encontrada() -> err.NoEncontrado:
    return err.NoEncontrado("ORDEN_NO_ENCONTRADA", "Esa orden de compra no existe.")


def _dinero_salida(monto: Decimal | None, moneda: str) -> DineroSalida | None:
    if monto is None:
        return None
    d = Dinero(monto, Moneda(moneda)).a_api()
    return DineroSalida(monto=d["monto"], moneda=d["moneda"])


def a_salida(o: OrdenCompra, recibido: dict[uuid.UUID, Decimal]) -> OrdenSalida:
    lineas = []
    total: Decimal | None = None
    for linea in o.lineas:
        rec = Cantidad(recibido.get(linea.id, Decimal(0)))
        ordenada = Cantidad(linea.cantidad_ordenada)
        if linea.costo_unitario_estimado is not None:
            total = (total or Decimal(0)) + linea.costo_unitario_estimado * linea.cantidad_ordenada
        lineas.append(
            LineaOrdenSalida(
                id=linea.id,
                producto=ProductoBreve(
                    id=linea.producto.id,
                    nombre=linea.producto.nombre,
                    sku=linea.producto.sku,
                    unidad_codigo=linea.producto.unidad_codigo,
                ),
                cantidad_ordenada=ordenada.a_api(),
                costo_unitario_estimado=_dinero_salida(linea.costo_unitario_estimado, o.moneda),
                cantidad_recibida=rec.a_api(),
                cantidad_pendiente=(ordenada - rec).a_api(),
            )
        )
    return OrdenSalida(
        id=o.id,
        numero=o.numero,
        proveedor=ProveedorBreve(id=o.proveedor.id, nombre=o.proveedor.nombre),
        estado=o.estado,  # type: ignore[arg-type]
        fecha_esperada=o.fecha_esperada,
        moneda=o.moneda,
        notas=o.notas,
        motivo_cierre=o.motivo_cierre,
        emitida_en=o.emitida_en,
        cerrada_en=o.cerrada_en,
        lineas=lineas,
        total_estimado=_dinero_salida(total, o.moneda) if total is not None else None,
        created_at=o.created_at,
    )


def _costo_en_moneda(costo: DineroEntrada | None, moneda: str) -> Decimal | None:
    if costo is None:
        return None
    if costo.moneda != moneda:
        raise err.ValidacionInvalida(
            "MONEDAS_DISTINTAS",
            f"El costo estimado debe ir en {moneda}, la moneda de la orden.",
            {"moneda": costo.moneda, "moneda_orden": moneda},
        )
    return Dinero.desde_api(costo.model_dump()).monto


async def _armar_lineas(
    sesion: AsyncSession, negocio_id: uuid.UUID, moneda: str, entradas: list[LineaOrdenEntrada]
) -> list[OrdenCompraLinea]:
    vistos: set[uuid.UUID] = set()
    lineas = []
    for posicion, entrada in enumerate(entradas, start=1):
        if entrada.producto_id in vistos:
            raise err.ValidacionInvalida(
                "PRODUCTO_REPETIDO",
                "Un producto solo puede aparecer una vez por orden; suma las cantidades.",
                {"producto_id": str(entrada.producto_id)},
            )
        vistos.add(entrada.producto_id)
        producto = await producto_operable(sesion, negocio_id, entrada.producto_id)
        cantidad = Cantidad(Decimal(entrada.cantidad))
        validar_cantidad_movimiento(cantidad, unidad_de(producto))
        lineas.append(
            OrdenCompraLinea(
                producto_id=producto.id,
                posicion=posicion,
                cantidad_ordenada=cantidad.valor,
                costo_unitario_estimado=_costo_en_moneda(entrada.costo_unitario_estimado, moneda),
            )
        )
    return lineas


async def _cargar(sesion: AsyncSession, negocio_id: uuid.UUID, orden_id: uuid.UUID) -> OrdenCompra:
    orden = await RepositorioOrdenes(sesion).por_id(negocio_id, orden_id)
    if orden is None:
        raise no_encontrada()
    return orden


async def _salida_de(
    sesion: AsyncSession, negocio_id: uuid.UUID, orden_id: uuid.UUID
) -> OrdenSalida:
    orden = await _cargar(sesion, negocio_id, orden_id)
    recibido = await RepositorioOrdenes(sesion).recibido_por_linea(orden.id)
    return a_salida(orden, recibido)


async def crear(sesion: AsyncSession, negocio_id: uuid.UUID, datos: OrdenNueva) -> OrdenSalida:
    repo = RepositorioOrdenes(sesion)
    async with sesion.begin():
        proveedor = await proveedor_seleccionable(sesion, negocio_id, datos.proveedor_id)
        moneda = (
            datos.moneda
            or (await RepositorioIdentidad(sesion).negocio_por_id(negocio_id)).moneda_base
        )
        Moneda(moneda)
        orden = OrdenCompra(
            negocio_id=negocio_id,
            proveedor_id=proveedor.id,
            secuencia=await repo.siguiente_secuencia(negocio_id),
            fecha_esperada=datos.fecha_esperada,
            moneda=moneda,
            notas=datos.notas,
        )
        orden.lineas = await _armar_lineas(sesion, negocio_id, moneda, datos.lineas)
        repo.guardar(orden)
        await sesion.flush()
        orden_id = orden.id
    async with sesion.begin():
        return await _salida_de(sesion, negocio_id, orden_id)


async def obtener(sesion: AsyncSession, negocio_id: uuid.UUID, orden_id: uuid.UUID) -> OrdenSalida:
    async with sesion.begin():
        return await _salida_de(sesion, negocio_id, orden_id)


def _exigir_borrador(orden: OrdenCompra) -> None:
    if orden.estado != "borrador":
        raise err.Conflicto(
            "ORDEN_NO_EDITABLE",
            f"La orden {orden.numero} está {orden.estado.replace('_', ' ')}; "
            "solo un borrador se edita.",
            {"orden_id": str(orden.id), "estado": orden.estado},
        )


async def editar(
    sesion: AsyncSession, negocio_id: uuid.UUID, orden_id: uuid.UUID, datos: OrdenEdicion
) -> OrdenSalida:
    """RF-COM-003: solo en borrador se editan las líneas."""
    async with sesion.begin():
        orden = await _cargar(sesion, negocio_id, orden_id)
        _exigir_borrador(orden)
        enviados = datos.model_fields_set
        if "fecha_esperada" in enviados:
            orden.fecha_esperada = datos.fecha_esperada
        if "notas" in enviados:
            orden.notas = datos.notas
        if datos.lineas is not None:
            orden.lineas = await _armar_lineas(sesion, negocio_id, orden.moneda, datos.lineas)
        await sesion.flush()
    async with sesion.begin():
        return await _salida_de(sesion, negocio_id, orden_id)


async def listar(
    sesion: AsyncSession,
    negocio_id: uuid.UUID,
    pagina: ParametrosPagina,
    *,
    proveedor_id: uuid.UUID | None = None,
    estado: str | None = None,
    desde: object = None,
    hasta: object = None,
) -> Pagina[OrdenSalida]:
    despues = None
    if pagina.cursor:
        despues = (int(decodificar_cursor(pagina.cursor)["s"]),)
    async with sesion.begin():
        repo = RepositorioOrdenes(sesion)
        filas = await repo.listar(
            negocio_id,
            proveedor_id=proveedor_id,
            estado=estado,
            desde=desde,  # type: ignore[arg-type]
            hasta=hasta,  # type: ignore[arg-type]
            limite=pagina.limit + 1,
            despues_de=despues,
        )
        salidas = [a_salida(o, await repo.recibido_por_linea(o.id)) for o in filas]
    secuencias = {o.id: o.secuencia for o in filas}
    return paginar(salidas, pagina.limit, clave_de=lambda s: {"s": secuencias[s.id]})


def _transicion_invalida(orden: OrdenCompra, accion: str) -> err.Conflicto:
    return err.Conflicto(
        "TRANSICION_INVALIDA",
        f"No se puede {accion} la orden {orden.numero}: está {orden.estado.replace('_', ' ')}.",
        {"orden_id": str(orden.id), "estado": orden.estado, "accion": accion},
    )


async def emitir(sesion: AsyncSession, negocio_id: uuid.UUID, orden_id: uuid.UUID) -> OrdenSalida:
    """RF-COM-003: borrador → emitida. Desde emitida ya se puede recibir."""
    async with sesion.begin():
        orden = await _cargar(sesion, negocio_id, orden_id)
        if orden.estado != "borrador":
            raise _transicion_invalida(orden, "emitir")
        orden.estado = "emitida"
        orden.emitida_en = datetime.now(UTC)
        await sesion.flush()
    async with sesion.begin():
        return await _salida_de(sesion, negocio_id, orden_id)


async def cancelar(
    sesion: AsyncSession, negocio_id: uuid.UUID, orden_id: uuid.UUID, datos: CancelacionEntrada
) -> OrdenSalida:
    """RF-COM-010: una orden sin recepciones se cancela con motivo. Con recepciones no: se
    cierra con faltante (RF-COM-008)."""
    async with sesion.begin():
        orden = await _cargar(sesion, negocio_id, orden_id)
        if await RepositorioOrdenes(sesion).tiene_recepciones(orden.id):
            raise err.Conflicto(
                "ORDEN_CON_RECEPCIONES",
                f"La orden {orden.numero} ya tiene recepciones; ciérrala con faltante.",
                {"orden_id": str(orden.id), "accion_sugerida": "cerrar-con-faltante"},
            )
        if orden.estado not in ("borrador", "emitida"):
            raise _transicion_invalida(orden, "cancelar")
        orden.estado = "cancelada"
        orden.motivo_cierre = datos.motivo
        orden.cerrada_en = datetime.now(UTC)
        await sesion.flush()
    async with sesion.begin():
        return await _salida_de(sesion, negocio_id, orden_id)


async def cerrar_con_faltante(
    sesion: AsyncSession, negocio_id: uuid.UUID, orden_id: uuid.UUID, datos: CierreFaltanteEntrada
) -> OrdenSalida:
    """RF-COM-008 / RN-12: una orden parcialmente recibida se cierra con faltante indicando el
    motivo, y desde entonces no admite más recepciones."""
    async with sesion.begin():
        orden = await _cargar(sesion, negocio_id, orden_id)
        if orden.estado != "parcialmente_recibida":
            raise _transicion_invalida(orden, "cerrar con faltante")
        orden.estado = "cerrada_con_faltante"
        orden.motivo_cierre = datos.motivo
        orden.cerrada_en = datetime.now(UTC)
        await sesion.flush()
    async with sesion.begin():
        return await _salida_de(sesion, negocio_id, orden_id)
