"""Recepciones (RF-COM-004..009, RF-COM-011, RF-COM-012, RN-08, RN-11, RN-12, RN-13).

Confirmar es atómico: o entran todas las líneas o no entra ninguna. La línea congela costo,
moneda, tasa y equivalente base; el costo actual del producto pasa a ser el último recibido.
"""

import uuid
from datetime import UTC, datetime
from decimal import Decimal

from sqlalchemy.ext.asyncio import AsyncSession

from app.api.deps import ContextoNegocio
from app.dominio import errores as err
from app.dominio.movimientos import TipoMovimiento, validar_cantidad_movimiento
from app.dominio.tipos import Cantidad, Dinero, Moneda
from app.esquemas.catalogo import DineroEntrada, DineroSalida
from app.esquemas.compras import (
    ConfirmacionRecepcion,
    LineaRecepcionEntrada,
    LineaRecepcionSalida,
    OrdenBreve,
    ProductoBreve,
    ProveedorBreve,
    RecepcionEdicion,
    RecepcionNueva,
    RecepcionSalida,
)
from app.infra.paginacion import Pagina, ParametrosPagina, decodificar_cursor, paginar
from app.modelos.compras import OrdenCompra, Recepcion, RecepcionLinea
from app.repositorios.compras import RepositorioOrdenes, RepositorioRecepciones
from app.repositorios.identidad import RepositorioIdentidad
from app.servicios.movimientos import aplicar_movimiento, producto_operable, unidad_de
from app.servicios.proveedores import proveedor_seleccionable

ESTADOS_RECIBIBLES = ("emitida", "parcialmente_recibida")


def no_encontrada() -> err.NoEncontrado:
    return err.NoEncontrado("RECEPCION_NO_ENCONTRADA", "Esa recepción no existe.")


def _dinero(monto: Decimal, moneda: str) -> DineroSalida:
    d = Dinero(monto, Moneda(moneda)).a_api()
    return DineroSalida(monto=d["monto"], moneda=d["moneda"])


def _tasa(t: Decimal | None) -> str | None:
    return None if t is None else str(t.quantize(Decimal("0.00000001")))


def a_salida(r: Recepcion, moneda_base: str, movimientos: list[uuid.UUID]) -> RecepcionSalida:
    lineas = []
    total = Decimal(0)
    total_base: Decimal | None = Decimal(0) if r.estado != "borrador" else None
    for linea in r.lineas:
        total += linea.costo_unitario * linea.cantidad_recibida
        if total_base is not None and linea.costo_unitario_base is not None:
            total_base += linea.costo_unitario_base * linea.cantidad_recibida
        lineas.append(
            LineaRecepcionSalida(
                id=linea.id,
                producto=ProductoBreve(
                    id=linea.producto.id,
                    nombre=linea.producto.nombre,
                    sku=linea.producto.sku,
                    unidad_codigo=linea.producto.unidad_codigo,
                ),
                orden_linea_id=linea.orden_linea_id,
                cantidad_recibida=Cantidad(linea.cantidad_recibida).a_api(),
                costo_unitario=_dinero(linea.costo_unitario, linea.moneda_costo),
                tasa_cambio=_tasa(linea.tasa_cambio),
                costo_unitario_base=(
                    _dinero(linea.costo_unitario_base, moneda_base)
                    if linea.costo_unitario_base is not None
                    else None
                ),
                exceso=linea.exceso,
            )
        )
    return RecepcionSalida(
        id=r.id,
        numero=r.numero,
        proveedor=ProveedorBreve(id=r.proveedor.id, nombre=r.proveedor.nombre),
        orden=OrdenBreve(id=r.orden.id, numero=r.orden.numero) if r.orden else None,
        estado=r.estado,  # type: ignore[arg-type]
        fecha=r.fecha,
        moneda=r.moneda,
        tasa_cambio=str(r.tasa_cambio.quantize(Decimal("0.00000001"))),
        notas=r.notas,
        confirmada_en=r.confirmada_en,
        lineas=lineas,
        total=_dinero(total.quantize(Decimal("0.0001")), r.moneda),
        total_base=(
            _dinero(total_base.quantize(Decimal("0.0001")), moneda_base)
            if total_base is not None
            else None
        ),
        movimientos_generados=movimientos,
        created_at=r.created_at,
    )


def _costo(costo: DineroEntrada, moneda: str) -> Decimal:
    if costo.moneda != moneda:
        raise err.ValidacionInvalida(
            "MONEDAS_DISTINTAS",
            f"El costo debe ir en {moneda}, la moneda de la recepción.",
            {"moneda": costo.moneda, "moneda_recepcion": moneda},
        )
    return Dinero.desde_api(costo.model_dump()).monto


def _tasa_de(moneda: str, moneda_base: str, tasa: str | None) -> Decimal:
    if moneda == moneda_base:
        return Decimal(1)
    if tasa is None:
        raise err.ValidacionInvalida(
            "TASA_OBLIGATORIA",
            f"La recepción va en {moneda}: indica la tasa de cambio a {moneda_base}.",
            {"moneda": moneda, "moneda_base": moneda_base},
        )
    valor = Decimal(tasa)
    if valor <= 0:
        raise err.ValidacionInvalida("TASA_INVALIDA", "La tasa de cambio debe ser mayor que cero.")
    return valor


async def _orden_recibible(
    sesion: AsyncSession, negocio_id: uuid.UUID, orden_id: uuid.UUID, proveedor_id: uuid.UUID
) -> OrdenCompra:
    orden = await RepositorioOrdenes(sesion).por_id(negocio_id, orden_id)
    if orden is None:
        raise err.NoEncontrado("ORDEN_NO_ENCONTRADA", "Esa orden de compra no existe.")
    if orden.proveedor_id != proveedor_id:
        raise err.ValidacionInvalida(
            "PROVEEDOR_NO_COINCIDE",
            f"La orden {orden.numero} es de otro proveedor.",
            {"orden_id": str(orden.id), "proveedor_id_orden": str(orden.proveedor_id)},
        )
    if orden.estado not in ESTADOS_RECIBIBLES:
        raise err.Conflicto(
            "ORDEN_NO_RECIBIBLE",
            f"La orden {orden.numero} está {orden.estado.replace('_', ' ')}; "
            "solo se recibe contra una emitida o parcialmente recibida.",
            {"orden_id": str(orden.id), "estado": orden.estado},
        )
    return orden


async def _armar_lineas(
    sesion: AsyncSession,
    negocio_id: uuid.UUID,
    moneda: str,
    entradas: list[LineaRecepcionEntrada],
    orden: OrdenCompra | None,
) -> list[RecepcionLinea]:
    lineas_orden = {linea.producto_id: linea for linea in orden.lineas} if orden else {}
    vistos: set[uuid.UUID] = set()
    lineas = []
    for posicion, entrada in enumerate(entradas, start=1):
        if entrada.producto_id in vistos:
            raise err.ValidacionInvalida(
                "PRODUCTO_REPETIDO",
                "Un producto solo puede aparecer una vez por recepción; suma las cantidades.",
                {"producto_id": str(entrada.producto_id)},
            )
        vistos.add(entrada.producto_id)
        producto = await producto_operable(sesion, negocio_id, entrada.producto_id)
        cantidad = Cantidad(Decimal(entrada.cantidad))
        validar_cantidad_movimiento(cantidad, unidad_de(producto))
        orden_linea_id = None
        if orden is not None:
            linea_orden = lineas_orden.get(producto.id)
            if linea_orden is None:
                raise err.ValidacionInvalida(
                    "PRODUCTO_FUERA_DE_ORDEN",
                    f"«{producto.nombre}» no está en la orden {orden.numero}. "
                    "Regístralo en una recepción directa.",
                    {"producto_id": str(producto.id), "orden_id": str(orden.id)},
                )
            orden_linea_id = linea_orden.id
        lineas.append(
            RecepcionLinea(
                producto_id=producto.id,
                orden_linea_id=orden_linea_id,
                posicion=posicion,
                cantidad_recibida=cantidad.valor,
                costo_unitario=_costo(entrada.costo_unitario, moneda),
                moneda_costo=moneda,
            )
        )
    return lineas


async def _cargar(
    sesion: AsyncSession, negocio_id: uuid.UUID, recepcion_id: uuid.UUID, *, bloquear: bool = False
) -> Recepcion:
    recepcion = await RepositorioRecepciones(sesion).por_id(
        negocio_id, recepcion_id, bloquear=bloquear
    )
    if recepcion is None:
        raise no_encontrada()
    return recepcion


async def _salida_de(
    sesion: AsyncSession, negocio_id: uuid.UUID, recepcion_id: uuid.UUID
) -> RecepcionSalida:
    recepcion = await _cargar(sesion, negocio_id, recepcion_id)
    moneda_base = (await RepositorioIdentidad(sesion).negocio_por_id(negocio_id)).moneda_base
    movimientos = await RepositorioRecepciones(sesion).movimientos_de(recepcion.id)
    return a_salida(recepcion, moneda_base, movimientos)


async def crear(
    sesion: AsyncSession, negocio_id: uuid.UUID, datos: RecepcionNueva
) -> RecepcionSalida:
    repo = RepositorioRecepciones(sesion)
    async with sesion.begin():
        proveedor = await proveedor_seleccionable(sesion, negocio_id, datos.proveedor_id)
        moneda_base = (await RepositorioIdentidad(sesion).negocio_por_id(negocio_id)).moneda_base
        moneda = datos.moneda or moneda_base
        Moneda(moneda)
        orden = None
        if datos.orden_id is not None:
            orden = await _orden_recibible(sesion, negocio_id, datos.orden_id, proveedor.id)
        recepcion = Recepcion(
            negocio_id=negocio_id,
            proveedor_id=proveedor.id,
            orden_id=orden.id if orden else None,
            secuencia=await repo.siguiente_secuencia(negocio_id),
            moneda=moneda,
            tasa_cambio=_tasa_de(moneda, moneda_base, datos.tasa_cambio),
            notas=datos.notas,
        )
        if datos.fecha is not None:
            recepcion.fecha = datos.fecha
        recepcion.lineas = await _armar_lineas(sesion, negocio_id, moneda, datos.lineas, orden)
        repo.guardar(recepcion)
        await sesion.flush()
        recepcion_id = recepcion.id
    async with sesion.begin():
        return await _salida_de(sesion, negocio_id, recepcion_id)


async def obtener(
    sesion: AsyncSession, negocio_id: uuid.UUID, recepcion_id: uuid.UUID
) -> RecepcionSalida:
    async with sesion.begin():
        return await _salida_de(sesion, negocio_id, recepcion_id)


def _inmutable(recepcion: Recepcion) -> err.Conflicto:
    return err.Conflicto(
        "RECEPCION_INMUTABLE",
        f"La recepción {recepcion.numero} ya está {recepcion.estado}. Para corregirla, anula sus "
        "movimientos.",
        {"recepcion_id": str(recepcion.id), "estado": recepcion.estado},
    )


async def editar(
    sesion: AsyncSession, negocio_id: uuid.UUID, recepcion_id: uuid.UUID, datos: RecepcionEdicion
) -> RecepcionSalida:
    """Solo un borrador se edita; una confirmada es inmutable (RF-COM-012)."""
    async with sesion.begin():
        recepcion = await _cargar(sesion, negocio_id, recepcion_id)
        if recepcion.estado != "borrador":
            raise _inmutable(recepcion)
        moneda_base = (await RepositorioIdentidad(sesion).negocio_por_id(negocio_id)).moneda_base
        enviados = datos.model_fields_set
        if "fecha" in enviados and datos.fecha is not None:
            recepcion.fecha = datos.fecha
        if "notas" in enviados:
            recepcion.notas = datos.notas
        if "tasa_cambio" in enviados:
            recepcion.tasa_cambio = _tasa_de(recepcion.moneda, moneda_base, datos.tasa_cambio)
        if datos.lineas is not None:
            orden = recepcion.orden
            recepcion.lineas = await _armar_lineas(
                sesion, negocio_id, recepcion.moneda, datos.lineas, orden
            )
        await sesion.flush()
    async with sesion.begin():
        return await _salida_de(sesion, negocio_id, recepcion_id)


async def confirmar(
    sesion: AsyncSession,
    contexto: ContextoNegocio,
    recepcion_id: uuid.UUID,
    datos: ConfirmacionRecepcion,
) -> RecepcionSalida:
    """RF-COM-006 / RN-13: una transacción. Genera una entrada por línea con motivo
    `recepcion_compra`, congela costos (RN-08), actualiza el costo actual (RF-COM-011) y el
    estado de la orden (RF-COM-008/009, RN-12). Si algo falla, no entra nada."""
    negocio_id = contexto.negocio_id
    async with sesion.begin():
        recepcion = await _cargar(sesion, negocio_id, recepcion_id, bloquear=True)
        if recepcion.estado != "borrador":
            raise _inmutable(recepcion)
        moneda_base = (await RepositorioIdentidad(sesion).negocio_por_id(negocio_id)).moneda_base
        tasa = recepcion.tasa_cambio if recepcion.moneda != moneda_base else Decimal(1)
        orden = None
        if recepcion.orden_id is not None:
            orden = await _orden_recibible(
                sesion, negocio_id, recepcion.orden_id, recepcion.proveedor_id
            )
            await _verificar_exceso(sesion, recepcion, orden, datos.confirmar_exceso)
        for linea in recepcion.lineas:
            producto = await producto_operable(sesion, negocio_id, linea.producto_id)
            cantidad = Cantidad(linea.cantidad_recibida)
            validar_cantidad_movimiento(cantidad, unidad_de(producto))
            linea.tasa_cambio = tasa
            linea.costo_unitario_base = (linea.costo_unitario * tasa).quantize(Decimal("0.0001"))
            await aplicar_movimiento(
                sesion,
                contexto,
                producto,
                tipo=TipoMovimiento.ENTRADA,
                cantidad=cantidad,
                direccion=1,
                motivo="recepcion_compra",
                nota=f"Recepción {recepcion.numero}",
                forzado=False,
                origen="recepcion",
                recepcion_id=recepcion.id,
                recepcion_linea_id=linea.id,
            )
            producto.costo_actual = linea.costo_unitario_base  # último costo recibido
        recepcion.estado = "confirmada"
        recepcion.confirmada_en = datetime.now(UTC)
        await sesion.flush()
        if orden is not None:
            await _actualizar_estado_orden(sesion, orden)
    async with sesion.begin():
        return await _salida_de(sesion, negocio_id, recepcion_id)


async def _verificar_exceso(
    sesion: AsyncSession, recepcion: Recepcion, orden: OrdenCompra, confirmar_exceso: bool
) -> None:
    """RF-COM-009 / RN-12: recibir más de lo ordenado exige confirmación explícita y queda
    marcado en la línea."""
    recibido = await RepositorioOrdenes(sesion).recibido_por_linea(orden.id)
    ordenadas = {linea.id: linea for linea in orden.lineas}
    excesos = []
    for linea in recepcion.lineas:
        if linea.orden_linea_id is None:
            continue
        linea_orden = ordenadas[linea.orden_linea_id]
        pendiente = linea_orden.cantidad_ordenada - recibido.get(linea_orden.id, Decimal(0))
        if linea.cantidad_recibida > pendiente:
            linea.exceso = True
            excesos.append(
                {
                    "producto_id": str(linea.producto_id),
                    "pendiente": Cantidad(max(pendiente, Decimal(0))).a_api(),
                    "recibido": Cantidad(linea.cantidad_recibida).a_api(),
                }
            )
    if excesos and not confirmar_exceso:
        raise err.Conflicto(
            "EXCESO_SOBRE_ORDEN",
            f"Estás recibiendo más de lo que pedía la orden {orden.numero}. "
            "Confirma el exceso para continuar.",
            {"orden_id": str(orden.id), "lineas": excesos, "confirmar_con": "confirmar_exceso"},
        )


async def _actualizar_estado_orden(sesion: AsyncSession, orden: OrdenCompra) -> None:
    """RF-COM-008: quedan pendientes → parcialmente recibida; nada pendiente → recibida."""
    recibido = await RepositorioOrdenes(sesion).recibido_por_linea(orden.id)
    pendiente = any(
        linea.cantidad_ordenada - recibido.get(linea.id, Decimal(0)) > 0 for linea in orden.lineas
    )
    orden.estado = "parcialmente_recibida" if pendiente else "recibida"


async def listar(
    sesion: AsyncSession,
    negocio_id: uuid.UUID,
    pagina: ParametrosPagina,
    *,
    proveedor_id: uuid.UUID | None = None,
    orden_id: uuid.UUID | None = None,
    estado: str | None = None,
    desde: object = None,
    hasta: object = None,
) -> Pagina[RecepcionSalida]:
    despues = None
    if pagina.cursor:
        despues = (int(decodificar_cursor(pagina.cursor)["s"]),)
    async with sesion.begin():
        repo = RepositorioRecepciones(sesion)
        filas = await repo.listar(
            negocio_id,
            proveedor_id=proveedor_id,
            orden_id=orden_id,
            estado=estado,
            desde=desde,  # type: ignore[arg-type]
            hasta=hasta,  # type: ignore[arg-type]
            limite=pagina.limit + 1,
            despues_de=despues,
        )
        moneda_base = (await RepositorioIdentidad(sesion).negocio_por_id(negocio_id)).moneda_base
        salidas = [a_salida(r, moneda_base, await repo.movimientos_de(r.id)) for r in filas]
    secuencias = {r.id: r.secuencia for r in filas}
    return paginar(salidas, pagina.limit, clave_de=lambda s: {"s": secuencias[s.id]})
