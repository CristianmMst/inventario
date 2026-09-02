"""Registrar movimientos (RF-INV-005, RF-INV-006, RN-03, RN-04, RNF-03).

Sigue plan.md §4.4 al pie de la letra, dentro de una sola transacción:
1. `SELECT … FOR UPDATE` sobre la fila de stock del producto.
2. Reglas del dominio con el stock ya bloqueado.
3. INSERT del movimiento.
4. UPDATE de la instantánea.
(5 y 6, eventos, llegan en H6 y se escriben en esta misma transacción.)
"""

import uuid
from datetime import datetime
from decimal import Decimal

import sqlalchemy as sa
from sqlalchemy.ext.asyncio import AsyncSession

from app.api.deps import ContextoNegocio
from app.dominio import errores as err
from app.dominio.movimientos import (
    Direccion,
    TipoMovimiento,
    signo_de,
    stock_resultante,
    validar_cantidad_movimiento,
)
from app.dominio.tipos import Cantidad, TipoUnidad, UnidadMedida
from app.esquemas.inventario import (
    AnulacionEntrada,
    AutorSalida,
    ConteoEntrada,
    ConteoSalida,
    MovimientoNuevo,
    MovimientoSalida,
)
from app.infra.paginacion import Pagina, ParametrosPagina, decodificar_cursor, paginar
from app.modelos import catalogo as mc
from app.modelos.compras import Recepcion
from app.modelos.inventario import Movimiento
from app.repositorios.catalogo import RepositorioProductos
from app.repositorios.movimientos import RepositorioMovimientos
from app.repositorios.stock import RepositorioStock
from app.servicios.motivos import validar_motivo

MOTIVOS_DEL_SISTEMA = frozenset({"recepcion_compra"})


def a_salida(m: Movimiento) -> MovimientoSalida:
    return MovimientoSalida(
        id=m.id,
        producto_id=m.producto_id,
        tipo=m.tipo,  # type: ignore[arg-type]
        cantidad=Cantidad(m.cantidad).a_api(),
        direccion=m.direccion,  # type: ignore[arg-type]
        motivo=m.motivo,
        nota=m.nota,
        forzado=m.forzado,
        stock_resultante=Cantidad(m.stock_resultante).a_api(),
        origen=m.origen,  # type: ignore[arg-type]
        autor=AutorSalida(tipo=m.autor_tipo, id=m.autor_id),  # type: ignore[arg-type]
        ocurrido_en=m.ocurrido_en,
        anulado_en=m.anulado_en,
        anula_movimiento_id=m.anula_movimiento_id,
        recepcion_id=m.recepcion_id,
        recepcion_linea_id=m.recepcion_linea_id,
    )


def origen_de(contexto: ContextoNegocio) -> str:
    return "app" if contexto.autor.tipo == "usuario" else "api"


async def producto_operable(
    sesion: AsyncSession, negocio_id: uuid.UUID, producto_id: uuid.UUID
) -> mc.Producto:
    """El producto existe en el negocio (404 si no, RF-AUT-007) y está activo (RF-CAT-011)."""
    producto = await RepositorioProductos(sesion).por_id(negocio_id, producto_id)
    if producto is None:
        raise err.NoEncontrado("PRODUCTO_NO_ENCONTRADO", "Ese producto no existe.")
    if producto.estado != "activo":
        raise err.Conflicto(
            "PRODUCTO_ARCHIVADO",
            f"«{producto.nombre}» está archivado y no admite movimientos. Desarchívalo primero.",
            {"producto_id": str(producto.id)},
        )
    return producto


def unidad_de(producto: mc.Producto) -> UnidadMedida:
    u = producto.unidad
    return UnidadMedida(u.codigo, u.nombre, TipoUnidad(u.tipo), u.decimales)


def _direccion(tipo: TipoMovimiento, direccion: Direccion | None) -> Direccion:
    if tipo.signo_fijo:
        return signo_de(tipo)
    if direccion is None:
        raise err.ValidacionInvalida(
            "DIRECCION_OBLIGATORIA",
            "Un ajuste debe indicar si suma o resta stock.",
            {"campo": "direccion"},
        )
    return direccion


async def aplicar_movimiento(
    sesion: AsyncSession,
    contexto: ContextoNegocio,
    producto: mc.Producto,
    *,
    tipo: TipoMovimiento,
    cantidad: Cantidad,
    direccion: Direccion,
    motivo: str,
    nota: str | None,
    forzado: bool,
    origen: str,
    anula_movimiento_id: uuid.UUID | None = None,
    recepcion_id: uuid.UUID | None = None,
    recepcion_linea_id: uuid.UUID | None = None,
) -> Movimiento:
    """Núcleo compartido por registro, anulación, conteo y recepción. Debe llamarse dentro de
    una transacción abierta: bloquea, valida el negativo, inserta y actualiza la instantánea."""
    stock = RepositorioStock(sesion)
    actual = await stock.bloquear(contexto.negocio_id, producto.id)
    nuevo = stock_resultante(actual, tipo, cantidad, direccion=direccion)
    # RN-03: salida y merma no dejan el stock bajo cero... salvo override explícito (RN-04).
    # Solo queda marcado como forzado el movimiento que de verdad saltó el bloqueo.
    salta_bloqueo = tipo.resta_stock and nuevo.valor < 0
    if salta_bloqueo and not forzado:
        raise err.Conflicto(
            "STOCK_INSUFICIENTE",
            f"Solo hay {actual.a_api()} de «{producto.nombre}» y pides {cantidad.a_api()}.",
            {
                "producto_id": str(producto.id),
                "solicitado": cantidad.a_api(),
                "disponible": actual.a_api(),
                "puede_forzar": tipo.resta_stock,
            },
        )
    movimiento = Movimiento(
        negocio_id=contexto.negocio_id,
        producto_id=producto.id,
        tipo=tipo.value,
        cantidad=cantidad.valor,
        direccion=direccion,
        motivo=motivo,
        nota=nota,
        forzado=salta_bloqueo,
        stock_resultante=nuevo.valor,
        anula_movimiento_id=anula_movimiento_id,
        recepcion_id=recepcion_id,
        recepcion_linea_id=recepcion_linea_id,
        origen=origen,
        autor_tipo=contexto.autor.tipo,
        autor_id=contexto.autor.id,
        # `now()` es la hora de inicio de la transacción: una que esperó al bloqueo quedaría
        # "antes" que la que se aplicó primero. clock_timestamp() ya con el bloqueo tomado
        # deja el historial en el orden real de aplicación (RF-INV-012).
        ocurrido_en=sa.func.clock_timestamp(),
    )
    RepositorioMovimientos(sesion).guardar(movimiento)
    await sesion.flush()
    await stock.actualizar(
        contexto.negocio_id, producto.id, nuevo, stock_minimo=producto.stock_minimo
    )
    await sesion.refresh(movimiento)
    return movimiento


async def registrar(
    sesion: AsyncSession, contexto: ContextoNegocio, datos: MovimientoNuevo
) -> MovimientoSalida:
    """POST /movimientos. El contramovimiento solo nace de una anulación (RF-INV-008) y el
    motivo `recepcion_compra` solo lo pone el sistema (RF-INV-010)."""
    tipo = TipoMovimiento(datos.tipo)
    if tipo is TipoMovimiento.CONTRAMOVIMIENTO:
        raise err.ValidacionInvalida(
            "TIPO_NO_PERMITIDO",
            "Un contramovimiento se crea anulando el movimiento original, no a mano.",
            {"tipo": tipo.value},
        )
    if datos.motivo in MOTIVOS_DEL_SISTEMA:
        raise err.ValidacionInvalida(
            "MOTIVO_RESERVADO",
            "Ese motivo lo asigna el sistema al confirmar una recepción.",
            {"motivo": datos.motivo},
        )
    cantidad = Cantidad(Decimal(datos.cantidad))
    direccion = _direccion(tipo, datos.direccion)
    async with sesion.begin():
        producto = await producto_operable(sesion, contexto.negocio_id, datos.producto_id)
        validar_cantidad_movimiento(cantidad, unidad_de(producto))
        await validar_motivo(sesion, tipo, datos.motivo, datos.nota, forzado=datos.forzar)
        movimiento = await aplicar_movimiento(
            sesion,
            contexto,
            producto,
            tipo=tipo,
            cantidad=cantidad,
            direccion=direccion,
            motivo=datos.motivo,
            nota=datos.nota,
            forzado=datos.forzar,
            origen=origen_de(contexto),
        )
        return a_salida(movimiento)


async def producto_existente(
    sesion: AsyncSession, negocio_id: uuid.UUID, producto_id: uuid.UUID
) -> mc.Producto:
    producto = await RepositorioProductos(sesion).por_id(negocio_id, producto_id)
    if producto is None:
        raise err.NoEncontrado("PRODUCTO_NO_ENCONTRADO", "Ese producto no existe.")
    return producto


def _movimiento_no_encontrado() -> err.NoEncontrado:
    return err.NoEncontrado("MOVIMIENTO_NO_ENCONTRADO", "Ese movimiento no existe.")


async def obtener(
    sesion: AsyncSession, negocio_id: uuid.UUID, movimiento_id: uuid.UUID
) -> MovimientoSalida:
    async with sesion.begin():
        movimiento = await RepositorioMovimientos(sesion).por_id(negocio_id, movimiento_id)
        if movimiento is None:
            raise _movimiento_no_encontrado()
        return a_salida(movimiento)


async def anular(
    sesion: AsyncSession,
    contexto: ContextoNegocio,
    movimiento_id: uuid.UUID,
    datos: AnulacionEntrada,
) -> MovimientoSalida:
    """RF-INV-008 / RN-02: contramovimiento de igual cantidad y signo contrario que referencia
    al original, con nota obligatoria; el original queda marcado. Un contramovimiento no se
    anula y un anulado no se vuelve a anular."""
    repo = RepositorioMovimientos(sesion)
    async with sesion.begin():
        original = await repo.por_id(contexto.negocio_id, movimiento_id)
        if original is None:
            raise _movimiento_no_encontrado()
        if original.tipo == TipoMovimiento.CONTRAMOVIMIENTO.value:
            raise err.Conflicto(
                "CONTRAMOVIMIENTO_NO_ANULABLE",
                "Una anulación no se anula. Si hace falta, registra el movimiento de nuevo.",
                {"movimiento_id": str(original.id)},
            )
        if original.anulado_en is not None:
            raise err.Conflicto(
                "MOVIMIENTO_YA_ANULADO",
                "Ese movimiento ya fue anulado.",
                {"movimiento_id": str(original.id), "anulado_en": original.anulado_en.isoformat()},
            )
        await validar_motivo(sesion, TipoMovimiento.CONTRAMOVIMIENTO, "anulacion", datos.nota)
        producto = await producto_operable(sesion, contexto.negocio_id, original.producto_id)
        contra = await aplicar_movimiento(
            sesion,
            contexto,
            producto,
            tipo=TipoMovimiento.CONTRAMOVIMIENTO,
            cantidad=Cantidad(original.cantidad),
            direccion=-original.direccion,  # type: ignore[arg-type]
            motivo="anulacion",
            nota=datos.nota,
            forzado=False,
            origen=origen_de(contexto),
            anula_movimiento_id=original.id,
        )
        # Marcar el original con la fila de stock ya bloqueada; si otra transacción lo anuló
        # entre la lectura y aquí, el WHERE no afecta filas y todo se revierte.
        marcadas = await repo.marcar_anulado(original.id)
        if marcadas != 1:
            raise err.Conflicto(
                "MOVIMIENTO_YA_ANULADO",
                "Ese movimiento ya fue anulado.",
                {"movimiento_id": str(original.id)},
            )
        if original.recepcion_id is not None:
            # RF-COM-012: la recepción confirmada no se edita; anular sus movimientos la
            # marca corregida sin borrarla.
            await sesion.execute(
                sa.update(Recepcion)
                .where(Recepcion.id == original.recepcion_id, Recepcion.estado == "confirmada")
                .values(estado="corregida")
            )
        return a_salida(contra)


async def contar(
    sesion: AsyncSession,
    contexto: ContextoNegocio,
    producto_id: uuid.UUID,
    datos: ConteoEntrada,
) -> ConteoSalida:
    """RF-INV-013 / RN-15: ajuste por la diferencia entre lo contado y el stock; delta cero no
    crea movimiento."""
    contada = Cantidad(Decimal(datos.cantidad_contada))
    async with sesion.begin():
        producto = await producto_operable(sesion, contexto.negocio_id, producto_id)
        contada.validar_para(unidad_de(producto))
        await validar_motivo(sesion, TipoMovimiento.AJUSTE, "conteo_fisico", datos.nota)
        actual = await RepositorioStock(sesion).bloquear(contexto.negocio_id, producto.id)
        diferencia = contada - actual
        movimiento = None
        if diferencia.valor != 0:
            movimiento = await aplicar_movimiento(
                sesion,
                contexto,
                producto,
                tipo=TipoMovimiento.AJUSTE,
                cantidad=Cantidad(abs(diferencia.valor)),
                direccion=1 if diferencia.valor > 0 else -1,
                motivo="conteo_fisico",
                nota=datos.nota,
                forzado=False,
                origen=origen_de(contexto),
            )
        return ConteoSalida(
            producto_id=producto.id,
            stock_anterior=actual.a_api(),
            cantidad_contada=contada.a_api(),
            diferencia=diferencia.a_api(),
            movimiento=a_salida(movimiento) if movimiento is not None else None,
        )


def _cursor_a_tupla(cursor: str | None) -> tuple[datetime, uuid.UUID] | None:
    if not cursor:
        return None
    c = decodificar_cursor(cursor)
    return datetime.fromisoformat(str(c["o"])), uuid.UUID(str(c["id"]))


def _paginar(filas: list[Movimiento], limite: int) -> Pagina[MovimientoSalida]:
    momentos = {m.id: m.ocurrido_en.isoformat() for m in filas}
    return paginar(
        [a_salida(m) for m in filas],
        limite,
        clave_de=lambda s: {"o": momentos[s.id], "id": str(s.id)},
    )


async def historial_producto(
    sesion: AsyncSession, negocio_id: uuid.UUID, producto_id: uuid.UUID, pagina: ParametrosPagina
) -> Pagina[MovimientoSalida]:
    """RF-INV-012 / RF-REP-004: historial del producto, paginado, con stock resultante y marca
    de anulado en cada fila. Un archivado conserva su historial (RF-CAT-011)."""
    async with sesion.begin():
        await producto_existente(sesion, negocio_id, producto_id)
        filas = await RepositorioMovimientos(sesion).listar(
            negocio_id,
            producto_id=producto_id,
            limite=pagina.limit + 1,
            despues_de=_cursor_a_tupla(pagina.cursor),
        )
    return _paginar(filas, pagina.limit)


async def listar(
    sesion: AsyncSession,
    negocio_id: uuid.UUID,
    pagina: ParametrosPagina,
    *,
    producto_id: uuid.UUID | None,
    tipo: TipoMovimiento | None,
    desde: datetime | None,
    hasta: datetime | None,
) -> Pagina[MovimientoSalida]:
    """RF-INV-002: consulta de movimientos con filtros por producto, tipo y rango de fechas."""
    async with sesion.begin():
        filas = await RepositorioMovimientos(sesion).listar(
            negocio_id,
            producto_id=producto_id,
            tipo=tipo.value if tipo else None,
            desde=desde,
            hasta=hasta,
            limite=pagina.limit + 1,
            despues_de=_cursor_a_tupla(pagina.cursor),
        )
    return _paginar(filas, pagina.limit)
