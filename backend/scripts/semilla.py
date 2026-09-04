"""Semilla de volumen de referencia (T-096): 20.000 productos, 180.000 movimientos, 12 meses.

Es la base sobre la que se miden RNF-01 a RNF-04 (tests/rendimiento). Crea un negocio propio
(`semilla@ejemplo.com`), 30 categorías, 20 proveedores, ~2.000 recepciones confirmadas con
sus entradas y el resto de movimientos repartidos en el último año. Escribe con COPY de
asyncpg: corre en bien menos de 5 minutos. No emite eventos: no forman parte del volumen que
miden los RNF y la tabla `eventos` se llena por los caminos normales de la API.

Uso (con la base de docker compose arriba):

    cd backend && uv run python scripts/semilla.py [--productos 20000 --movimientos 180000]

Idempotente en lo esencial: si el negocio ya existe, se borran sus datos y se vuelve a sembrar.
"""

from __future__ import annotations

import argparse
import asyncio
import random
import sys
import time
import uuid
from datetime import UTC, date, datetime, timedelta
from decimal import Decimal
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import sqlalchemy as sa  # noqa: E402

from app.esquemas.auth import NegocioNuevo, Registro  # noqa: E402
from app.infra import db  # noqa: E402
from app.servicios import auth  # noqa: E402

EMAIL = "semilla@ejemplo.com"
PASSWORD = "password"
SEMILLA_ALEATORIA = 20260901

ADJETIVOS = [
    "rayado",
    "cuadriculado",
    "liso",
    "grande",
    "pequeño",
    "doble",
    "premium",
    "escolar",
    "básico",
    "reforzado",
]
SUSTANTIVOS = [
    "Cuaderno",
    "Lápiz",
    "Bolígrafo",
    "Marcador",
    "Resma",
    "Carpeta",
    "Tijeras",
    "Pegante",
    "Regla",
    "Borrador",
    "Sacapuntas",
    "Agenda",
    "Libreta",
    "Sobre",
    "Cinta",
    "Grapadora",
    "Clip",
    "Cartulina",
    "Papel",
    "Tinta",
    "Tóner",
    "Calculadora",
    "Compás",
    "Escuadra",
    "Témpera",
    "Pincel",
    "Block",
    "Separador",
    "Rotulador",
    "Corrector",
]
COMPLEMENTOS = [
    "100 hojas",
    "50 hojas",
    "A4",
    "carta",
    "oficio",
    "x12",
    "x24",
    "0,5 mm",
    "0,7 mm",
    "colores",
    "negro",
    "azul",
]
CATEGORIAS = [
    "Papelería",
    "Escritura",
    "Oficina",
    "Arte",
    "Escolar",
    "Archivo",
    "Impresión",
    "Adhesivos",
    "Corte",
    "Medición",
    "Cuadernos",
    "Agendas",
    "Sobres",
    "Tintas",
    "Calculadoras",
    "Dibujo",
    "Pinturas",
    "Manualidades",
    "Regalos",
    "Mochilas",
    "Tecnología",
    "Limpieza",
    "Cafetería",
    "Decoración",
    "Juguetería",
    "Libros",
    "Revistas",
    "Mapas",
    "Etiquetas",
    "Varios",
]
PROVEEDORES = [
    f"Distribuidora {n}"
    for n in [
        "Norte",
        "Sur",
        "Andina",
        "Caribe",
        "Central",
        "Pacífico",
        "Oriente",
        "Occidente",
        "Capital",
        "Llanos",
    ]
] + [
    f"Importadora {n}"
    for n in [
        "Papelera",
        "Escolar",
        "Global",
        "Express",
        "Total",
        "Uno",
        "Dos",
        "Tres",
        "Cuatro",
        "Cinco",
    ]
]
MOTIVOS_SALIDA = ["venta", "venta", "venta", "venta", "consumo_interno"]
MOTIVOS_MERMA = ["rotura", "vencimiento", "robo", "perdida"]


def ean13(semilla: int) -> str:
    base = f"770{semilla:09d}"[:12]
    suma = sum(int(d) * (1 if i % 2 == 0 else 3) for i, d in enumerate(base))
    return base + str((10 - suma % 10) % 10)


def q3(valor: float | Decimal) -> Decimal:
    return Decimal(valor).quantize(Decimal("0.001"))


def q4(valor: float | Decimal) -> Decimal:
    return Decimal(valor).quantize(Decimal("0.0001"))


async def limpiar_negocio(conn, negocio_id: uuid.UUID) -> None:  # noqa: ANN001
    """Borra los datos del negocio de semilla respetando el trigger de inmutabilidad."""
    await conn.execute("ALTER TABLE movimientos DISABLE TRIGGER movimientos_inmutables")
    for tabla in [
        "eventos",
        "facturas_imagenes",
        "facturas_recepciones",
        "facturas",
        "movimientos",
        "recepciones_lineas",
        "recepciones",
        "ordenes_compra_lineas",
        "ordenes_compra",
        "stock_productos",
        "codigos_barras",
        "productos",
        "categorias",
        "proveedores",
        "operaciones_idempotentes",
        "suscripciones_webhook",
        "api_keys",
    ]:
        if tabla in (
            "ordenes_compra_lineas",
            "recepciones_lineas",
            "facturas_recepciones",
            "facturas_imagenes",
        ):
            padre = {
                "ordenes_compra_lineas": ("orden_id", "ordenes_compra"),
                "recepciones_lineas": ("recepcion_id", "recepciones"),
                "facturas_recepciones": ("factura_id", "facturas"),
                "facturas_imagenes": ("factura_id", "facturas"),
            }[tabla]
            await conn.execute(
                f"DELETE FROM {tabla} WHERE {padre[0]} IN (SELECT id FROM {padre[1]} WHERE negocio_id = $1)",
                negocio_id,
            )
        else:
            await conn.execute(f"DELETE FROM {tabla} WHERE negocio_id = $1", negocio_id)
    await conn.execute("ALTER TABLE movimientos ENABLE TRIGGER movimientos_inmutables")


async def asegurar_negocio() -> tuple[uuid.UUID, uuid.UUID]:
    """Devuelve (negocio_id, usuario_id); crea la cuenta de semilla si no existe."""
    async with db.fabrica_sesiones()() as sesion:
        fila = (
            await sesion.execute(
                sa.text(
                    "SELECT m.negocio_id, u.id FROM usuarios u JOIN membresias m ON m.usuario_id = u.id WHERE u.email = :email"
                ),
                {"email": EMAIL},
            )
        ).first()
        if fila:
            return fila[0], fila[1]
        # La consulta abrió una transacción implícita; `registrar` abre la suya.
        await sesion.rollback()
        datos = Registro(
            email=EMAIL,
            password=PASSWORD,
            nombre="Semilla",
            negocio=NegocioNuevo(
                nombre="Papelería de referencia", moneda_base="COP", zona_horaria="America/Bogota"
            ),
        )
        resultado = await auth.registrar(sesion, datos)
        return resultado.negocio.id, resultado.usuario.id


async def sembrar(n_productos: int, n_movimientos: int) -> None:
    rng = random.Random(SEMILLA_ALEATORIA)
    inicio = time.perf_counter()
    negocio_id, usuario_id = await asegurar_negocio()
    ahora = datetime.now(UTC)
    hace_un_ano = ahora - timedelta(days=365)

    async with db.motor().begin() as conexion:
        raw = await conexion.get_raw_connection()
        conn = raw.driver_connection  # asyncpg
        await limpiar_negocio(conn, negocio_id)

        # Categorías y proveedores.
        categorias = [(uuid.uuid4(), negocio_id, nombre) for nombre in CATEGORIAS]
        # `nombre` es citext y COPY binario no lo codifica: son 30 filas, va por INSERT.
        await conn.executemany(
            "INSERT INTO categorias (id, negocio_id, nombre) VALUES ($1, $2, $3)", categorias
        )
        proveedores = [
            (uuid.uuid4(), negocio_id, nombre, f"9{i:08d}") for i, nombre in enumerate(PROVEEDORES)
        ]
        await conn.copy_records_to_table(
            "proveedores",
            records=proveedores,
            columns=["id", "negocio_id", "nombre", "identificacion_fiscal"],
        )

        # Productos y códigos de barras.
        productos = []
        codigos = []
        for i in range(n_productos):
            pid = uuid.uuid4()
            nombre = f"{rng.choice(SUSTANTIVOS)} {rng.choice(ADJETIVOS)} {rng.choice(COMPLEMENTOS)} #{i + 1}"
            costo = q4(rng.uniform(500, 90000))
            precio = q4(float(costo) * rng.uniform(1.2, 1.9))
            minimo = q3(rng.choice([0, 2, 3, 5, 10, 20])) if rng.random() < 0.7 else None
            unidad = "unidad" if rng.random() < 0.9 else rng.choice(["caja", "paquete"])
            categoria_id = rng.choice(categorias)[0]
            creado = hace_un_ano + timedelta(days=rng.uniform(0, 300))
            productos.append(
                (
                    pid,
                    negocio_id,
                    f"P-{i + 1:06d}",
                    nombre,
                    categoria_id,
                    unidad,
                    costo,
                    precio,
                    minimo,
                    "activo",
                    creado,
                    creado,
                )
            )
            codigos.append((uuid.uuid4(), negocio_id, pid, ean13(i + 1)))
        await conn.copy_records_to_table(
            "productos",
            records=productos,
            columns=[
                "id",
                "negocio_id",
                "sku",
                "nombre",
                "categoria_id",
                "unidad_codigo",
                "costo_actual",
                "precio_venta",
                "stock_minimo",
                "estado",
                "created_at",
                "updated_at",
            ],
        )
        await conn.copy_records_to_table(
            "codigos_barras", records=codigos, columns=["id", "negocio_id", "producto_id", "codigo"]
        )
        print(f"productos: {n_productos} ({time.perf_counter() - inicio:.1f}s)")

        # Recepciones confirmadas (~10 % de las entradas vienen de compras).
        n_recepciones = max(200, n_movimientos // 90)
        recepciones = []
        lineas_recepcion = []
        movimientos = []
        stock: dict[uuid.UUID, Decimal] = {p[0]: Decimal("0") for p in productos}
        for r in range(n_recepciones):
            rid = uuid.uuid4()
            proveedor_id = rng.choice(proveedores)[0]
            fecha_dt = hace_un_ano + timedelta(days=rng.uniform(0, 364), hours=rng.uniform(8, 18))
            recepciones.append(
                (
                    rid,
                    negocio_id,
                    proveedor_id,
                    r + 1,
                    fecha_dt.date(),
                    "COP",
                    Decimal("1.00000000"),
                    "confirmada",
                    fecha_dt,
                    fecha_dt,
                    fecha_dt,
                )
            )
            for pos, prod in enumerate(rng.sample(productos, k=rng.randint(2, 6)), start=1):
                lid = uuid.uuid4()
                cantidad = q3(rng.choice([6, 12, 24, 50, 100]))
                costo = q4(float(prod[6]) * rng.uniform(0.9, 1.1))
                lineas_recepcion.append(
                    (
                        lid,
                        rid,
                        prod[0],
                        pos,
                        cantidad,
                        costo,
                        "COP",
                        Decimal("1.00000000"),
                        costo,
                        False,
                    )
                )
                stock[prod[0]] += cantidad
                movimientos.append(
                    (
                        uuid.uuid4(),
                        negocio_id,
                        prod[0],
                        "entrada",
                        cantidad,
                        1,
                        "recepcion_compra",
                        None,
                        False,
                        stock[prod[0]],
                        None,
                        None,
                        rid,
                        lid,
                        "recepcion",
                        "usuario",
                        usuario_id,
                        fecha_dt,
                    )
                )
        await conn.copy_records_to_table(
            "recepciones",
            records=recepciones,
            columns=[
                "id",
                "negocio_id",
                "proveedor_id",
                "secuencia",
                "fecha",
                "moneda",
                "tasa_cambio",
                "estado",
                "confirmada_en",
                "created_at",
                "updated_at",
            ],
        )
        await conn.copy_records_to_table(
            "recepciones_lineas",
            records=lineas_recepcion,
            columns=[
                "id",
                "recepcion_id",
                "producto_id",
                "posicion",
                "cantidad_recibida",
                "costo_unitario",
                "moneda_costo",
                "tasa_cambio",
                "costo_unitario_base",
                "exceso",
            ],
        )
        print(
            f"recepciones: {n_recepciones} con {len(lineas_recepcion)} líneas ({time.perf_counter() - inicio:.1f}s)"
        )

        # Resto de movimientos: carga inicial por producto y luego salidas, mermas y ajustes.
        restantes = n_movimientos - len(movimientos)
        por_producto = max(1, restantes // n_productos)
        columnas_mov = [
            "id",
            "negocio_id",
            "producto_id",
            "tipo",
            "cantidad",
            "direccion",
            "motivo",
            "nota",
            "forzado",
            "stock_resultante",
            "anulado_en",
            "anula_movimiento_id",
            "recepcion_id",
            "recepcion_linea_id",
            "origen",
            "autor_tipo",
            "autor_id",
            "ocurrido_en",
        ]
        generados = 0
        lote: list[tuple] = []
        for prod in productos:
            pid = prod[0]
            creado: datetime = prod[10]
            momento = creado + timedelta(hours=1)
            carga = q3(rng.choice([10, 20, 30, 50, 80, 120]))
            stock[pid] += carga
            lote.append(
                (
                    uuid.uuid4(),
                    negocio_id,
                    pid,
                    "entrada",
                    carga,
                    1,
                    "carga_inicial",
                    "Carga inicial de la semilla",
                    False,
                    stock[pid],
                    None,
                    None,
                    None,
                    None,
                    "app",
                    "usuario",
                    usuario_id,
                    momento,
                )
            )
            generados += 1
            for _ in range(por_producto - 1):
                if generados >= restantes:
                    break
                momento = momento + timedelta(hours=rng.uniform(6, 240))
                if momento > ahora:
                    break
                azar = rng.random()
                if azar < 0.72:
                    cantidad = q3(rng.randint(1, 5))
                    if stock[pid] - cantidad < 0:
                        continue
                    stock[pid] -= cantidad
                    lote.append(
                        (
                            uuid.uuid4(),
                            negocio_id,
                            pid,
                            "salida",
                            cantidad,
                            -1,
                            rng.choice(MOTIVOS_SALIDA),
                            None,
                            False,
                            stock[pid],
                            None,
                            None,
                            None,
                            None,
                            "app",
                            "usuario",
                            usuario_id,
                            momento,
                        )
                    )
                elif azar < 0.82:
                    cantidad = q3(rng.randint(1, 3))
                    if stock[pid] - cantidad < 0:
                        continue
                    stock[pid] -= cantidad
                    lote.append(
                        (
                            uuid.uuid4(),
                            negocio_id,
                            pid,
                            "merma",
                            cantidad,
                            -1,
                            rng.choice(MOTIVOS_MERMA),
                            "Merma de la semilla",
                            False,
                            stock[pid],
                            None,
                            None,
                            None,
                            None,
                            "app",
                            "usuario",
                            usuario_id,
                            momento,
                        )
                    )
                elif azar < 0.92:
                    cantidad = q3(rng.randint(5, 40))
                    stock[pid] += cantidad
                    lote.append(
                        (
                            uuid.uuid4(),
                            negocio_id,
                            pid,
                            "entrada",
                            cantidad,
                            1,
                            "carga_inicial",
                            None,
                            False,
                            stock[pid],
                            None,
                            None,
                            None,
                            None,
                            "api",
                            "usuario",
                            usuario_id,
                            momento,
                        )
                    )
                else:
                    cantidad = q3(rng.randint(1, 4))
                    direccion = 1 if rng.random() < 0.5 or stock[pid] - cantidad < 0 else -1
                    stock[pid] += cantidad * direccion
                    lote.append(
                        (
                            uuid.uuid4(),
                            negocio_id,
                            pid,
                            "ajuste",
                            cantidad,
                            direccion,
                            "conteo_fisico",
                            "Conteo físico de la semilla",
                            False,
                            stock[pid],
                            None,
                            None,
                            None,
                            None,
                            "app",
                            "usuario",
                            usuario_id,
                            momento,
                        )
                    )
                generados += 1
            if len(lote) >= 20000:
                await conn.copy_records_to_table("movimientos", records=lote, columns=columnas_mov)
                lote = []
        # Relleno hasta la cifra objetivo: salidas descartadas por falta de stock se compensan
        # con entradas y salidas pequeñas sobre productos al azar (siempre con stock suficiente).
        while generados < restantes:
            prod = rng.choice(productos)
            pid = prod[0]
            momento = hace_un_ano + timedelta(days=rng.uniform(65, 364))
            if momento < prod[10]:
                momento = prod[10] + timedelta(hours=rng.uniform(2, 48))
            cantidad = q3(rng.randint(1, 3))
            if stock[pid] - cantidad >= 0 and rng.random() < 0.6:
                stock[pid] -= cantidad
                lote.append(
                    (
                        uuid.uuid4(),
                        negocio_id,
                        pid,
                        "salida",
                        cantidad,
                        -1,
                        "venta",
                        None,
                        False,
                        stock[pid],
                        None,
                        None,
                        None,
                        None,
                        "app",
                        "usuario",
                        usuario_id,
                        momento,
                    )
                )
            else:
                stock[pid] += cantidad
                lote.append(
                    (
                        uuid.uuid4(),
                        negocio_id,
                        pid,
                        "entrada",
                        cantidad,
                        1,
                        "carga_inicial",
                        None,
                        False,
                        stock[pid],
                        None,
                        None,
                        None,
                        None,
                        "api",
                        "usuario",
                        usuario_id,
                        momento,
                    )
                )
            generados += 1
            if len(lote) >= 20000:
                await conn.copy_records_to_table("movimientos", records=lote, columns=columnas_mov)
                lote = []
        # Las entradas de recepción también van, en su fecha.
        lote.extend(movimientos)
        await conn.copy_records_to_table("movimientos", records=lote, columns=columnas_mov)
        total_mov = await conn.fetchval(
            "SELECT count(*) FROM movimientos WHERE negocio_id = $1", negocio_id
        )
        print(f"movimientos: {total_mov} ({time.perf_counter() - inicio:.1f}s)")

        # Instantánea de stock: la suma de movimientos, con la bandera de bajo mínimo (RN-22).
        await conn.execute(
            """
            INSERT INTO stock_productos (producto_id, negocio_id, cantidad, bajo_minimo, actualizado_en)
            SELECT p.id, p.negocio_id, coalesce(s.total, 0),
                   p.stock_minimo IS NOT NULL AND coalesce(s.total, 0) <= p.stock_minimo, now()
            FROM productos p
            LEFT JOIN (SELECT producto_id, sum(cantidad * direccion) AS total FROM movimientos WHERE negocio_id = $1 GROUP BY producto_id) s
              ON s.producto_id = p.id
            WHERE p.negocio_id = $1
            """,
            negocio_id,
        )
        await conn.execute(
            "ANALYZE productos, movimientos, stock_productos, codigos_barras, recepciones, recepciones_lineas"
        )

    print(
        f"listo en {time.perf_counter() - inicio:.1f}s · negocio {negocio_id} · usuario {EMAIL} / {PASSWORD}"
    )
    print(f"código de barras de ejemplo: {ean13(1)} · fecha de corte: {date.today().isoformat()}")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--productos", type=int, default=20_000)
    parser.add_argument("--movimientos", type=int, default=180_000)
    args = parser.parse_args()
    asyncio.run(sembrar(args.productos, args.movimientos))


if __name__ == "__main__":
    main()
