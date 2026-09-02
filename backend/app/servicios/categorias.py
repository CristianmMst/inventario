"""RF-CAT-005: categorías planas con nombre único por negocio."""

import uuid

from sqlalchemy.exc import IntegrityError
from sqlalchemy.ext.asyncio import AsyncSession

from app.dominio import errores as err
from app.esquemas.catalogo import CategoriaEdicion, CategoriaNueva, CategoriaSalida
from app.infra.paginacion import Pagina, ParametrosPagina, decodificar_cursor, paginar
from app.modelos.catalogo import Categoria
from app.repositorios.catalogo import RepositorioCategorias


def _salida(c: Categoria) -> CategoriaSalida:
    return CategoriaSalida(id=c.id, nombre=c.nombre)


def _duplicada(nombre: str) -> err.Conflicto:
    return err.Conflicto(
        "CATEGORIA_DUPLICADA", f"Ya existe una categoría llamada «{nombre}».", {"nombre": nombre}
    )


def _no_encontrada() -> err.NoEncontrado:
    return err.NoEncontrado("CATEGORIA_NO_ENCONTRADA", "Esa categoría no existe.")


async def crear(
    sesion: AsyncSession, negocio_id: uuid.UUID, datos: CategoriaNueva
) -> CategoriaSalida:
    repo = RepositorioCategorias(sesion)
    categoria = Categoria(negocio_id=negocio_id, nombre=datos.nombre)
    try:
        async with sesion.begin():
            repo.guardar(categoria)
    except IntegrityError as e:
        raise _duplicada(datos.nombre) from e
    return _salida(categoria)


async def editar(
    sesion: AsyncSession, negocio_id: uuid.UUID, categoria_id: uuid.UUID, datos: CategoriaEdicion
) -> CategoriaSalida:
    repo = RepositorioCategorias(sesion)
    try:
        async with sesion.begin():
            categoria = await repo.por_id(negocio_id, categoria_id)
            if categoria is None:
                raise _no_encontrada()
            categoria.nombre = datos.nombre
    except IntegrityError as e:
        raise _duplicada(datos.nombre) from e
    return _salida(categoria)


async def listar(
    sesion: AsyncSession, negocio_id: uuid.UUID, pagina: ParametrosPagina
) -> Pagina[CategoriaSalida]:
    repo = RepositorioCategorias(sesion)
    despues = None
    if pagina.cursor:
        c = decodificar_cursor(pagina.cursor)
        despues = (str(c["n"]), uuid.UUID(str(c["id"])))
    filas = await repo.listar(negocio_id, pagina.limit + 1, despues)
    return paginar(
        [_salida(f) for f in filas],
        pagina.limit,
        clave_de=lambda c: {"n": c.nombre, "id": str(c.id)},
    )
