"""RF-INV-003, RF-INV-004, RN-01: la instantánea de stock coincide con la suma de movimientos."""

import random
import uuid
from decimal import Decimal

import httpx
import sqlalchemy as sa
from sqlalchemy.ext.asyncio import AsyncSession

from app.dominio.movimientos import TipoMovimiento
from app.dominio.tipos import Cantidad
from app.modelos.catalogo import Producto
from app.modelos.identidad import Negocio
from app.modelos.inventario import Movimiento
from app.repositorios.stock import RepositorioStock

AUTOR = uuid.uuid4()


async def _producto(sesion: AsyncSession, negocio_id: uuid.UUID, sku: str) -> uuid.UUID:
    producto = Producto(negocio_id=negocio_id, sku=sku, nombre=sku, unidad_codigo="kg")
    sesion.add(producto)
    await sesion.flush()
    return producto.id


async def _negocio(sesion: AsyncSession) -> uuid.UUID:
    negocio = Negocio(nombre="P", moneda_base="COP", zona_horaria="UTC")
    sesion.add(negocio)
    await sesion.flush()
    return negocio.id


async def _aplicar(
    sesion: AsyncSession,
    repo: RepositorioStock,
    negocio_id: uuid.UUID,
    producto_id: uuid.UUID,
    tipo: TipoMovimiento,
    cantidad: Decimal,
    direccion: int,
) -> None:
    """Imita lo que hará el servicio de T-029: bloquear, insertar el movimiento con su stock
    resultante y actualizar la instantánea, todo en la misma transacción."""
    async with sesion.begin():
        actual = await repo.bloquear(negocio_id, producto_id)
        nuevo = actual + Cantidad(cantidad) if direccion == 1 else actual - Cantidad(cantidad)
        sesion.add(
            Movimiento(
                negocio_id=negocio_id,
                producto_id=producto_id,
                tipo=tipo.value,
                cantidad=cantidad,
                direccion=direccion,
                motivo="otro",
                nota="prueba",
                stock_resultante=nuevo.valor,
                origen="api",
                autor_tipo="usuario",
                autor_id=AUTOR,
            )
        )
        await repo.actualizar(negocio_id, producto_id, nuevo, stock_minimo=None)


async def test_rf_inv_003_tras_una_bateria_aleatoria_la_instantanea_es_igual_a_la_suma(
    sesion: AsyncSession,
) -> None:
    negocio_id = await _negocio(sesion)
    productos = [await _producto(sesion, negocio_id, f"P-{i}") for i in range(4)]
    await sesion.commit()
    repo = RepositorioStock(sesion)
    aleatorio = random.Random(20260902)
    for _ in range(120):
        producto_id = aleatorio.choice(productos)
        tipo, direccion = aleatorio.choice(
            [
                (TipoMovimiento.ENTRADA, 1),
                (TipoMovimiento.SALIDA, -1),
                (TipoMovimiento.MERMA, -1),
                (TipoMovimiento.AJUSTE, 1),
                (TipoMovimiento.AJUSTE, -1),
            ]
        )
        cantidad = Decimal(aleatorio.randint(1, 5000)) / Decimal(1000)
        await _aplicar(sesion, repo, negocio_id, producto_id, tipo, cantidad, direccion)

    discrepancias = await repo.reconciliar(negocio_id)
    assert discrepancias == []
    for producto_id in productos:
        suma = (
            await sesion.execute(
                sa.text(
                    "select coalesce(sum(cantidad * direccion), 0) from movimientos"
                    " where producto_id = :p"
                ),
                {"p": producto_id},
            )
        ).scalar_one()
        instantanea = await repo.actual(negocio_id, producto_id)
        assert instantanea.valor == Decimal(suma).quantize(Decimal("0.001"))


async def test_rf_inv_003_un_producto_sin_movimientos_tiene_stock_cero(
    sesion: AsyncSession,
) -> None:
    negocio_id = await _negocio(sesion)
    producto_id = await _producto(sesion, negocio_id, "P")
    await sesion.commit()
    assert (await RepositorioStock(sesion).actual(negocio_id, producto_id)).valor == Decimal("0")


async def test_rn_01_la_reconciliacion_detecta_una_instantanea_manipulada(
    sesion: AsyncSession,
) -> None:
    negocio_id = await _negocio(sesion)
    producto_id = await _producto(sesion, negocio_id, "P")
    await sesion.commit()
    repo = RepositorioStock(sesion)
    await _aplicar(sesion, repo, negocio_id, producto_id, TipoMovimiento.ENTRADA, Decimal(10), 1)
    await sesion.execute(
        sa.text("update stock_productos set cantidad = 99 where producto_id = :p"),
        {"p": producto_id},
    )
    await sesion.commit()
    discrepancias = await repo.reconciliar(negocio_id)
    assert len(discrepancias) == 1
    assert discrepancias[0].producto_id == producto_id
    assert discrepancias[0].instantanea == Decimal("99.000")
    assert discrepancias[0].suma == Decimal("10.000")


async def test_rf_inv_004_bloquear_crea_la_fila_de_stock_si_no_existe_y_la_bloquea(
    sesion: AsyncSession,
) -> None:
    negocio_id = await _negocio(sesion)
    producto_id = await _producto(sesion, negocio_id, "P")
    await sesion.commit()
    repo = RepositorioStock(sesion)
    async with sesion.begin():
        assert (await repo.bloquear(negocio_id, producto_id)).valor == Decimal("0")
    filas = (
        await sesion.execute(
            sa.text("select count(*) from stock_productos where producto_id = :p"),
            {"p": producto_id},
        )
    ).scalar_one()
    assert filas == 1


async def test_rn_01_ningun_endpoint_escribe_el_stock_directamente(
    cliente: httpx.AsyncClient,
) -> None:
    openapi = (await cliente.get("/openapi.json")).json()
    for ruta, metodos in openapi["paths"].items():
        if "stock" in ruta:
            assert set(metodos) <= {"get"}, ruta
