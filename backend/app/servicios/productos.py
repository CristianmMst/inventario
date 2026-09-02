"""RF-CAT-001, RF-CAT-002, RF-CAT-010, RF-CAT-013: alta, ficha y edición de producto."""

import secrets
import uuid
from decimal import Decimal

from sqlalchemy.exc import IntegrityError
from sqlalchemy.ext.asyncio import AsyncSession

from app.dominio import errores as err
from app.dominio.tipos import Cantidad, Dinero, Moneda, TipoUnidad, UnidadMedida
from app.esquemas.catalogo import (
    CategoriaSalida,
    DineroEntrada,
    DineroSalida,
    ProductoEdicion,
    ProductoNuevo,
    ProductoSalida,
    UnidadMedidaSalida,
)
from app.infra.paginacion import Pagina, ParametrosPagina, decodificar_cursor, paginar
from app.modelos import catalogo as m
from app.repositorios.catalogo import (
    RepositorioBusqueda,
    RepositorioCategorias,
    RepositorioCodigosBarras,
    RepositorioProductos,
    RepositorioUnidades,
)
from app.repositorios.identidad import RepositorioIdentidad


def no_encontrado() -> err.NoEncontrado:
    return err.NoEncontrado("PRODUCTO_NO_ENCONTRADO", "Ese producto no existe.")


def a_salida(p: m.Producto, moneda_base: str) -> ProductoSalida:
    def dinero(monto: Decimal | None, moneda: str) -> DineroSalida | None:
        if monto is None:
            return None
        d = Dinero(monto, Moneda(moneda)).a_api()
        return DineroSalida(monto=d["monto"], moneda=d["moneda"])

    return ProductoSalida(
        id=p.id,
        sku=p.sku,
        nombre=p.nombre,
        categoria=(
            CategoriaSalida(id=p.categoria.id, nombre=p.categoria.nombre) if p.categoria else None
        ),
        unidad=UnidadMedidaSalida(
            codigo=p.unidad.codigo,
            nombre=p.unidad.nombre,
            tipo=p.unidad.tipo,  # type: ignore[arg-type]
            decimales=p.unidad.decimales,
        ),
        costo_actual=dinero(p.costo_actual, moneda_base),
        precio_venta=dinero(p.precio_venta, moneda_base),
        stock_minimo=Cantidad(p.stock_minimo).a_api() if p.stock_minimo is not None else None,
        estado=p.estado,  # type: ignore[arg-type]
        codigos_barras=[c.codigo for c in p.codigos_barras],
    )


def _unidad_dominio(u: m.UnidadMedida) -> UnidadMedida:
    return UnidadMedida(u.codigo, u.nombre, TipoUnidad(u.tipo), u.decimales)


def _monto_en_base(dinero: DineroEntrada | None, moneda_base: str, campo: str) -> Decimal | None:
    """RF-CAT-013: costo y precio van en la moneda base del negocio."""
    if dinero is None:
        return None
    if dinero.moneda != moneda_base:
        raise err.ValidacionInvalida(
            "MONEDA_NO_ES_LA_BASE",
            f"El {campo} debe ir en {moneda_base}, la moneda del negocio.",
            {"campo": campo, "moneda": dinero.moneda, "moneda_base": moneda_base},
        )
    return Dinero.desde_api(dinero.model_dump()).monto


def _stock_minimo(valor: str | None, unidad: UnidadMedida) -> Decimal | None:
    if valor is None:
        return None
    cantidad = Cantidad.desde_api(valor)
    cantidad.validar_para(unidad)
    return cantidad.valor


def _generar_sku() -> str:
    return f"P-{secrets.token_hex(4).upper()}"


class _Contexto:
    """Carga lo que la validación necesita de la base: unidad, categoría y moneda base."""

    def __init__(self, sesion: AsyncSession, negocio_id: uuid.UUID) -> None:
        self.s = sesion
        self.negocio_id = negocio_id
        self.productos = RepositorioProductos(sesion)

    async def moneda_base(self) -> str:
        return (await RepositorioIdentidad(self.s).negocio_por_id(self.negocio_id)).moneda_base

    async def unidad(self, codigo: str) -> m.UnidadMedida:
        unidad = await RepositorioUnidades(self.s).por_codigo(codigo)
        if unidad is None:
            raise err.ValidacionInvalida(
                "UNIDAD_DESCONOCIDA", "Esa unidad de medida no existe.", {"unidad_codigo": codigo}
            )
        return unidad

    async def categoria(self, categoria_id: uuid.UUID | None) -> None:
        if categoria_id is None:
            return
        if await RepositorioCategorias(self.s).por_id(self.negocio_id, categoria_id) is None:
            raise err.ValidacionInvalida(
                "CATEGORIA_DESCONOCIDA",
                "Esa categoría no existe.",
                {"categoria_id": str(categoria_id)},
            )

    async def sku_disponible(self, sku: str | None) -> str:
        if sku is None:
            while True:
                candidato = _generar_sku()
                if not await self.productos.existe_sku(self.negocio_id, candidato):
                    return candidato
        return sku


async def _codigo_duplicado(
    sesion: AsyncSession, negocio_id: uuid.UUID, codigo: str
) -> err.Conflicto:
    """RN-05: el 409 dice a qué producto pertenece ya el código."""
    duenio = await RepositorioCodigosBarras(sesion).producto_duenio(negocio_id, codigo)
    return err.Conflicto(
        "CODIGO_BARRAS_DUPLICADO",
        f"El código {codigo} ya está asignado a «{duenio.nombre if duenio else '?'}».",
        {
            "codigo": codigo,
            "producto_id": str(duenio.id) if duenio else None,
            "producto_nombre": duenio.nombre if duenio else None,
        },
    )


async def _asegurar_codigos_libres(
    sesion: AsyncSession, negocio_id: uuid.UUID, codigos: list[str]
) -> None:
    repo = RepositorioCodigosBarras(sesion)
    for codigo in codigos:
        if await repo.por_codigo(negocio_id, codigo) is not None:
            raise await _codigo_duplicado(sesion, negocio_id, codigo)


def _sku_duplicado(sku: str) -> err.Conflicto:
    return err.Conflicto(
        "SKU_DUPLICADO", f"Ya existe un producto con el SKU «{sku}».", {"sku": sku}
    )


async def crear(
    sesion: AsyncSession, negocio_id: uuid.UUID, datos: ProductoNuevo
) -> ProductoSalida:
    ctx = _Contexto(sesion, negocio_id)
    try:
        async with sesion.begin():
            moneda_base = await ctx.moneda_base()
            unidad = await ctx.unidad(datos.unidad_codigo)
            await ctx.categoria(datos.categoria_id)
            codigos = sorted(set(datos.codigos_barras))
            await _asegurar_codigos_libres(sesion, negocio_id, codigos)
            producto = m.Producto(
                negocio_id=negocio_id,
                sku=await ctx.sku_disponible(datos.sku),
                nombre=datos.nombre,
                categoria_id=datos.categoria_id,
                unidad_codigo=unidad.codigo,
                costo_actual=_monto_en_base(datos.costo_actual, moneda_base, "costo"),
                precio_venta=_monto_en_base(datos.precio_venta, moneda_base, "precio"),
                stock_minimo=_stock_minimo(datos.stock_minimo, _unidad_dominio(unidad)),
            )
            ctx.productos.guardar(producto)
            await sesion.flush()
            for codigo in codigos:
                RepositorioCodigosBarras(sesion).guardar(
                    m.CodigoBarras(negocio_id=negocio_id, producto_id=producto.id, codigo=codigo)
                )
            producto_id = producto.id
    except IntegrityError as e:
        raise _sku_duplicado(datos.sku or "") from e
    return await obtener(sesion, negocio_id, producto_id)


async def obtener(
    sesion: AsyncSession, negocio_id: uuid.UUID, producto_id: uuid.UUID
) -> ProductoSalida:
    async with sesion.begin():
        producto = await RepositorioProductos(sesion).por_id(negocio_id, producto_id)
        if producto is None:
            raise no_encontrado()
        return a_salida(producto, await _Contexto(sesion, negocio_id).moneda_base())


async def editar(
    sesion: AsyncSession, negocio_id: uuid.UUID, producto_id: uuid.UUID, datos: ProductoEdicion
) -> ProductoSalida:
    """RF-CAT-010: los cambios de costo y precio sobrescriben el valor actual (RN-09)."""
    ctx = _Contexto(sesion, negocio_id)
    enviados = datos.model_fields_set
    try:
        async with sesion.begin():
            producto = await ctx.productos.por_id(negocio_id, producto_id)
            if producto is None:
                raise no_encontrado()
            moneda_base = await ctx.moneda_base()
            if "nombre" in enviados and datos.nombre is not None:
                producto.nombre = datos.nombre
            if "sku" in enviados and datos.sku is not None:
                producto.sku = datos.sku
            if "unidad_codigo" in enviados and datos.unidad_codigo is not None:
                producto.unidad_codigo = (await ctx.unidad(datos.unidad_codigo)).codigo
            if "categoria_id" in enviados:
                await ctx.categoria(datos.categoria_id)
                producto.categoria_id = datos.categoria_id
            if "costo_actual" in enviados:
                producto.costo_actual = _monto_en_base(datos.costo_actual, moneda_base, "costo")
            if "precio_venta" in enviados:
                producto.precio_venta = _monto_en_base(datos.precio_venta, moneda_base, "precio")
            if "stock_minimo" in enviados:
                unidad = await ctx.unidad(producto.unidad_codigo)
                producto.stock_minimo = _stock_minimo(datos.stock_minimo, _unidad_dominio(unidad))
    except IntegrityError as e:
        raise _sku_duplicado(datos.sku or "") from e
    return await obtener(sesion, negocio_id, producto_id)


async def agregar_codigo(
    sesion: AsyncSession, negocio_id: uuid.UUID, producto_id: uuid.UUID, codigo: str
) -> ProductoSalida:
    """RF-CAT-003: asigna un código más al producto. Ya usado responde 409 con el dueño (RN-05)."""
    repo = RepositorioCodigosBarras(sesion)
    async with sesion.begin():
        producto = await RepositorioProductos(sesion).por_id(negocio_id, producto_id)
        if producto is None:
            raise no_encontrado()
        if await repo.por_codigo(negocio_id, codigo) is not None:
            raise await _codigo_duplicado(sesion, negocio_id, codigo)
        repo.guardar(m.CodigoBarras(negocio_id=negocio_id, producto_id=producto_id, codigo=codigo))
    return await obtener(sesion, negocio_id, producto_id)


async def quitar_codigo(
    sesion: AsyncSession, negocio_id: uuid.UUID, producto_id: uuid.UUID, codigo: str
) -> None:
    repo = RepositorioCodigosBarras(sesion)
    async with sesion.begin():
        producto = await RepositorioProductos(sesion).por_id(negocio_id, producto_id)
        if producto is None:
            raise no_encontrado()
        existente = await repo.por_codigo(negocio_id, codigo)
        if existente is None or existente.producto_id != producto_id:
            raise err.NoEncontrado(
                "CODIGO_BARRAS_NO_ENCONTRADO",
                "Ese producto no tiene ese código.",
                {"codigo": codigo},
            )
        await repo.borrar(existente)


async def por_codigo_barras(
    sesion: AsyncSession, negocio_id: uuid.UUID, codigo: str
) -> ProductoSalida:
    """RF-CAT-008 / RF-CAT-009: el 404 incluye el código consultado para que la app ofrezca el
    alta precargada sin una segunda llamada. Nada se crea solo (RN-14)."""
    async with sesion.begin():
        producto = await RepositorioBusqueda(sesion).por_codigo_barras(negocio_id, codigo)
        if producto is None:
            raise err.NoEncontrado(
                "PRODUCTO_NO_ENCONTRADO",
                f"Ningún producto tiene el código {codigo}.",
                {"codigo": codigo},
            )
        return a_salida(producto, await _Contexto(sesion, negocio_id).moneda_base())


async def buscar(
    sesion: AsyncSession, negocio_id: uuid.UUID, texto: str, pagina: ParametrosPagina
) -> Pagina[ProductoSalida]:
    """RF-CAT-007: búsqueda por texto paginada por relevancia."""
    texto = texto.strip()
    if not texto:
        raise err.ValidacionInvalida("BUSQUEDA_VACIA", "Escribe algo para buscar.")
    despues = None
    if pagina.cursor:
        c = decodificar_cursor(pagina.cursor)
        despues = (float(c["r"]), uuid.UUID(str(c["id"])))
    async with sesion.begin():
        filas = await RepositorioBusqueda(sesion).por_texto(
            negocio_id, texto, pagina.limit + 1, despues
        )
        moneda_base = await _Contexto(sesion, negocio_id).moneda_base()
    rangos = {p.id: r for p, r in filas}
    return paginar(
        [a_salida(p, moneda_base) for p, _ in filas],
        pagina.limit,
        clave_de=lambda s: {"r": rangos[s.id], "id": str(s.id)},
    )
