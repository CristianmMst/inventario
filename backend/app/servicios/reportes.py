"""Los siete reportes (RF-REP-001..008). Todos paginan lo que puede crecer y responden igual
con JWT y con API key (RF-INT-008)."""

import uuid
from datetime import date, datetime
from decimal import Decimal
from typing import Any

from sqlalchemy.ext.asyncio import AsyncSession

from app.dominio import errores as err
from app.dominio.tipos import Cantidad, Dinero, Moneda
from app.esquemas.catalogo import DineroSalida
from app.esquemas.compras import ProductoBreve, ProveedorBreve
from app.esquemas.reportes import (
    CategoriaBreve,
    ComprasCategoria,
    ComprasProveedor,
    FilaAgotado,
    FilaBajoMinimo,
    FilaDiscrepancia,
    FilaNoValorizable,
    FilaSinMovimiento,
    Lista,
    MermaMotivo,
    MermaProducto,
    ResumenCompras,
    ResumenMermas,
    ValorCategoria,
    Valorizacion,
)
from app.infra.paginacion import ParametrosPagina, decodificar_cursor, paginar
from app.repositorios.identidad import RepositorioIdentidad
from app.repositorios.reportes import RepositorioReportes


def _producto(fila: Any) -> ProductoBreve:
    return ProductoBreve(
        id=fila.producto_id,
        nombre=fila.producto_nombre,
        sku=fila.producto_sku,
        unidad_codigo=fila.producto_unidad,
    )


def _dinero(monto: Decimal, moneda: str) -> DineroSalida:
    d = Dinero(monto.quantize(Decimal("0.0001")), Moneda(moneda)).a_api()
    return DineroSalida(monto=d["monto"], moneda=d["moneda"])


def _cantidad(valor: Decimal | None) -> str:
    return Cantidad(Decimal(valor if valor is not None else 0)).a_api()


def _cursor_nombre(pagina: ParametrosPagina) -> tuple[str, uuid.UUID] | None:
    if not pagina.cursor:
        return None
    c = decodificar_cursor(pagina.cursor)
    return str(c["n"]), uuid.UUID(str(c["id"]))


def _rango(desde: date, hasta: date) -> None:
    if desde > hasta:
        raise err.ValidacionInvalida(
            "RANGO_INVALIDO",
            "La fecha inicial no puede ser posterior a la final.",
            {"desde": desde.isoformat(), "hasta": hasta.isoformat()},
        )


async def _moneda_base(sesion: AsyncSession, negocio_id: uuid.UUID) -> str:
    return (await RepositorioIdentidad(sesion).negocio_por_id(negocio_id)).moneda_base


async def bajo_minimo(
    sesion: AsyncSession, negocio_id: uuid.UUID, pagina: ParametrosPagina
) -> Lista[FilaBajoMinimo]:
    despues = None
    if pagina.cursor:
        c = decodificar_cursor(pagina.cursor)
        despues = (Decimal(str(c["r"])), uuid.UUID(str(c["id"])))
    async with sesion.begin():
        filas = await RepositorioReportes(sesion).bajo_minimo(negocio_id, pagina.limit + 1, despues)
    relativos = {
        f.producto_id: Decimal(f.deficit_relativo).quantize(Decimal("0.0001")) for f in filas
    }
    salidas = [
        FilaBajoMinimo(
            producto=_producto(f),
            stock_actual=_cantidad(f.stock),
            stock_minimo=_cantidad(f.stock_minimo),
            deficit=_cantidad(f.deficit),
            deficit_relativo=str(relativos[f.producto_id]),
        )
        for f in filas
    ]
    pag = paginar(
        salidas,
        pagina.limit,
        clave_de=lambda s: {"r": str(relativos[s.producto.id]), "id": str(s.producto.id)},
    )
    return Lista(datos=pag.datos, cursor_siguiente=pag.cursor_siguiente, tiene_mas=pag.tiene_mas)


async def agotados(
    sesion: AsyncSession, negocio_id: uuid.UUID, pagina: ParametrosPagina
) -> Lista[FilaAgotado]:
    async with sesion.begin():
        filas = await RepositorioReportes(sesion).agotados(
            negocio_id, pagina.limit + 1, _cursor_nombre(pagina)
        )
    salidas = [
        FilaAgotado(
            producto=_producto(f),
            stock_actual=_cantidad(f.stock),
            stock_minimo=_cantidad(f.stock_minimo) if f.stock_minimo is not None else None,
        )
        for f in filas
    ]
    pag = paginar(
        salidas, pagina.limit, clave_de=lambda s: {"n": s.producto.nombre, "id": str(s.producto.id)}
    )
    return Lista(datos=pag.datos, cursor_siguiente=pag.cursor_siguiente, tiene_mas=pag.tiene_mas)


async def sin_movimiento(
    sesion: AsyncSession, negocio_id: uuid.UUID, pagina: ParametrosPagina, *, dias: int
) -> Lista[FilaSinMovimiento]:
    async with sesion.begin():
        moneda_base = await _moneda_base(sesion, negocio_id)
        filas = await RepositorioReportes(sesion).sin_movimiento(
            negocio_id, dias, pagina.limit + 1, _cursor_nombre(pagina)
        )
    salidas = [
        FilaSinMovimiento(
            producto=_producto(f),
            stock_actual=_cantidad(f.stock),
            valor_a_costo=(
                _dinero(Decimal(f.stock) * Decimal(f.costo_actual), moneda_base)
                if f.costo_actual is not None
                else None
            ),
            ultimo_movimiento_en=f.ultimo_movimiento_en,
            creado_en=f.created_at,
        )
        for f in filas
    ]
    pag = paginar(
        salidas, pagina.limit, clave_de=lambda s: {"n": s.producto.nombre, "id": str(s.producto.id)}
    )
    return Lista(datos=pag.datos, cursor_siguiente=pag.cursor_siguiente, tiene_mas=pag.tiene_mas)


async def valorizacion(
    sesion: AsyncSession, negocio_id: uuid.UUID, pagina: ParametrosPagina
) -> Valorizacion:
    async with sesion.begin():
        moneda_base = await _moneda_base(sesion, negocio_id)
        repo = RepositorioReportes(sesion)
        total, productos, categorias = await repo.valorizacion(negocio_id)
        no_val = await repo.no_valorizables(negocio_id, pagina.limit + 1, _cursor_nombre(pagina))
    filas_nv = [
        FilaNoValorizable(producto=_producto(f), stock_actual=_cantidad(f.stock)) for f in no_val
    ]
    pag = paginar(
        filas_nv,
        pagina.limit,
        clave_de=lambda s: {"n": s.producto.nombre, "id": str(s.producto.id)},
    )
    return Valorizacion(
        total=_dinero(total, moneda_base),
        productos_valorizados=productos,
        por_categoria=[
            ValorCategoria(
                categoria=(
                    CategoriaBreve(id=c.categoria_id, nombre=c.categoria_nombre)
                    if c.categoria_id is not None
                    else None
                ),
                productos=int(c.productos),
                valor=_dinero(Decimal(c.valor), moneda_base),
            )
            for c in categorias
        ],
        no_valorizables=Lista(
            datos=pag.datos, cursor_siguiente=pag.cursor_siguiente, tiene_mas=pag.tiene_mas
        ),
    )


async def compras(
    sesion: AsyncSession, negocio_id: uuid.UUID, desde: date, hasta: date
) -> ResumenCompras:
    _rango(desde, hasta)
    async with sesion.begin():
        moneda_base = await _moneda_base(sesion, negocio_id)
        datos = await RepositorioReportes(sesion).compras(negocio_id, desde, hasta)
    recibidos, facturados, nombres = datos["recibidos"], datos["facturados"], datos["proveedores"]
    por_proveedor = []
    for pid in sorted(set(recibidos) | set(facturados), key=lambda i: nombres.get(i, "")):
        rec = recibidos.get(pid)
        fac = facturados.get(pid)
        por_proveedor.append(
            ComprasProveedor(
                proveedor=ProveedorBreve(id=pid, nombre=nombres.get(pid, "")),
                total_recibido=_dinero(Decimal(rec.recibido) if rec else Decimal(0), moneda_base),
                total_facturado=_dinero(Decimal(fac.facturado) if fac else Decimal(0), moneda_base),
            )
        )
    total_recibido = sum((Decimal(r.recibido) for r in recibidos.values()), Decimal(0))
    total_facturado = sum((Decimal(f.facturado) for f in facturados.values()), Decimal(0))
    return ResumenCompras(
        desde=desde.isoformat(),
        hasta=hasta.isoformat(),
        total_recibido=_dinero(total_recibido, moneda_base),
        total_facturado=_dinero(total_facturado, moneda_base),
        recepciones=sum(int(r.recepciones) for r in recibidos.values()),
        facturas=sum(int(f.facturas) for f in facturados.values()),
        por_proveedor=por_proveedor,
        por_categoria=[
            ComprasCategoria(
                categoria=(
                    CategoriaBreve(id=c.categoria_id, nombre=c.categoria_nombre)
                    if c.categoria_id is not None
                    else None
                ),
                total_recibido=_dinero(Decimal(c.recibido), moneda_base),
            )
            for c in datos["por_categoria"]
        ],
    )


async def mermas(
    sesion: AsyncSession, negocio_id: uuid.UUID, pagina: ParametrosPagina, desde: date, hasta: date
) -> ResumenMermas:
    _rango(desde, hasta)
    async with sesion.begin():
        moneda_base = await _moneda_base(sesion, negocio_id)
        datos = await RepositorioReportes(sesion).mermas(negocio_id, desde, hasta)
    por_motivo = [
        MermaMotivo(
            motivo=m.motivo,
            etiqueta=m.etiqueta,
            cantidad=_cantidad(m.cantidad),
            valor=_dinero(Decimal(m.valor), moneda_base),
        )
        for m in datos["por_motivo"]
    ]
    productos = [
        MermaProducto(
            producto=_producto(p),
            cantidad=_cantidad(p.cantidad),
            valor=_dinero(Decimal(p.valor), moneda_base),
        )
        for p in datos["por_producto"]
    ]
    # El desglose por producto puede crecer: se pagina en memoria por posición.
    inicio = int(decodificar_cursor(pagina.cursor)["i"]) if pagina.cursor else 0
    tramo = productos[inicio : inicio + pagina.limit + 1]
    posiciones = {p.producto.id: inicio + i for i, p in enumerate(tramo)}
    pag = paginar(tramo, pagina.limit, clave_de=lambda s: {"i": posiciones[s.producto.id] + 1})
    return ResumenMermas(
        desde=desde.isoformat(),
        hasta=hasta.isoformat(),
        total_cantidad=_cantidad(
            sum((Decimal(m.cantidad) for m in datos["por_motivo"]), Decimal(0))
        ),
        total_valor=_dinero(
            sum((Decimal(m.valor) for m in datos["por_motivo"]), Decimal(0)), moneda_base
        ),
        por_motivo=por_motivo,
        por_producto=Lista(
            datos=pag.datos, cursor_siguiente=pag.cursor_siguiente, tiene_mas=pag.tiene_mas
        ),
    )


async def discrepancias(
    sesion: AsyncSession, negocio_id: uuid.UUID, pagina: ParametrosPagina, desde: date, hasta: date
) -> Lista[FilaDiscrepancia]:
    _rango(desde, hasta)
    despues = None
    if pagina.cursor:
        c = decodificar_cursor(pagina.cursor)
        despues = (datetime.fromisoformat(str(c["o"])), uuid.UUID(str(c["id"])))
    async with sesion.begin():
        filas = await RepositorioReportes(sesion).discrepancias(
            negocio_id, desde, hasta, pagina.limit + 1, despues
        )
    momentos = {f.Movimiento.id: f.Movimiento.ocurrido_en.isoformat() for f in filas}
    salidas = [
        FilaDiscrepancia(
            movimiento_id=f.Movimiento.id,
            producto=_producto(f),
            tipo=f.Movimiento.tipo,
            cantidad=_cantidad(f.Movimiento.cantidad),
            stock_resultante=_cantidad(f.Movimiento.stock_resultante),
            motivo=f.Movimiento.motivo,
            nota=f.Movimiento.nota,
            ocurrido_en=f.Movimiento.ocurrido_en,
            autor_tipo=f.Movimiento.autor_tipo,
        )
        for f in filas
    ]
    pag = paginar(
        salidas,
        pagina.limit,
        clave_de=lambda s: {"o": momentos[s.movimiento_id], "id": str(s.movimiento_id)},
    )
    return Lista(datos=pag.datos, cursor_siguiente=pag.cursor_siguiente, tiene_mas=pag.tiene_mas)
