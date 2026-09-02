"""Registrar movimientos (RF-INV-005, RF-INV-006, RN-03, RN-04, RNF-03).

Sigue plan.md §4.4 al pie de la letra, dentro de una sola transacción:
1. `SELECT … FOR UPDATE` sobre la fila de stock del producto.
2. Reglas del dominio con el stock ya bloqueado.
3. INSERT del movimiento.
4. UPDATE de la instantánea.
(5 y 6, eventos, llegan en H6 y se escriben en esta misma transacción.)
"""

import uuid
from decimal import Decimal

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
from app.esquemas.inventario import AutorSalida, MovimientoNuevo, MovimientoSalida
from app.modelos import catalogo as mc
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
    recepcion_linea_id: uuid.UUID | None = None,
) -> Movimiento:
    """Núcleo compartido por registro, anulación, conteo y recepción. Debe llamarse dentro de
    una transacción abierta: bloquea, valida el negativo, inserta y actualiza la instantánea."""
    stock = RepositorioStock(sesion)
    actual = await stock.bloquear(contexto.negocio_id, producto.id)
    nuevo = stock_resultante(actual, tipo, cantidad, direccion=direccion)
    if direccion == -1 and nuevo.valor < 0 and not forzado:
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
        forzado=forzado,
        stock_resultante=nuevo.valor,
        anula_movimiento_id=anula_movimiento_id,
        recepcion_linea_id=recepcion_linea_id,
        origen=origen,
        autor_tipo=contexto.autor.tipo,
        autor_id=contexto.autor.id,
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
