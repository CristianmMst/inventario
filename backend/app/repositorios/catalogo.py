import uuid

import sqlalchemy as sa
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.orm import selectinload

from app.modelos.catalogo import Categoria, CodigoBarras, Producto, UnidadMedida


class RepositorioUnidades:
    """Catálogo global: es la única tabla de negocio sin `negocio_id`, por ser semilla."""

    def __init__(self, sesion: AsyncSession) -> None:
        self._s = sesion

    async def listar(self, limite: int, despues_de_orden: int | None) -> list[UnidadMedida]:
        consulta = sa.select(UnidadMedida).order_by(UnidadMedida.orden).limit(limite)
        if despues_de_orden is not None:
            consulta = consulta.where(UnidadMedida.orden > despues_de_orden)
        return list((await self._s.execute(consulta)).scalars())

    async def por_codigo(self, codigo: str) -> UnidadMedida | None:
        return await self._s.get(UnidadMedida, codigo)


class RepositorioCategorias:
    def __init__(self, sesion: AsyncSession) -> None:
        self._s = sesion

    def guardar(self, categoria: Categoria) -> None:
        self._s.add(categoria)

    async def por_id(self, negocio_id: uuid.UUID, categoria_id: uuid.UUID) -> Categoria | None:
        return (
            await self._s.execute(
                sa.select(Categoria).where(
                    Categoria.negocio_id == negocio_id, Categoria.id == categoria_id
                )
            )
        ).scalar_one_or_none()

    async def listar(
        self, negocio_id: uuid.UUID, limite: int, despues_de: tuple[str, uuid.UUID] | None
    ) -> list[Categoria]:
        consulta = (
            sa.select(Categoria)
            .where(Categoria.negocio_id == negocio_id)
            .order_by(Categoria.nombre, Categoria.id)
            .limit(limite)
        )
        if despues_de is not None:
            nombre, cid = despues_de
            consulta = consulta.where(
                sa.tuple_(Categoria.nombre, Categoria.id) > sa.tuple_(nombre, cid)
            )
        return list((await self._s.execute(consulta)).scalars())


class RepositorioProductos:
    def __init__(self, sesion: AsyncSession) -> None:
        self._s = sesion

    def guardar(self, producto: Producto) -> None:
        self._s.add(producto)

    async def por_id(self, negocio_id: uuid.UUID, producto_id: uuid.UUID) -> Producto | None:
        return (
            await self._s.execute(
                sa.select(Producto)
                .where(Producto.negocio_id == negocio_id, Producto.id == producto_id)
                .options(
                    selectinload(Producto.categoria),
                    selectinload(Producto.unidad),
                    selectinload(Producto.codigos_barras),
                )
                .execution_options(populate_existing=True)
            )
        ).scalar_one_or_none()

    async def existe_sku(self, negocio_id: uuid.UUID, sku: str) -> bool:
        return (
            await self._s.execute(
                sa.select(sa.literal(True)).where(
                    Producto.negocio_id == negocio_id, Producto.sku == sku
                )
            )
        ).scalar_one_or_none() is True


class RepositorioCodigosBarras:
    def __init__(self, sesion: AsyncSession) -> None:
        self._s = sesion

    async def por_codigo(self, negocio_id: uuid.UUID, codigo: str) -> CodigoBarras | None:
        return (
            await self._s.execute(
                sa.select(CodigoBarras).where(
                    CodigoBarras.negocio_id == negocio_id, CodigoBarras.codigo == codigo
                )
            )
        ).scalar_one_or_none()

    async def producto_duenio(self, negocio_id: uuid.UUID, codigo: str) -> Producto | None:
        """El producto al que pertenece un código, para explicar el 409 (RN-05)."""
        return (
            await self._s.execute(
                sa.select(Producto)
                .join(CodigoBarras, CodigoBarras.producto_id == Producto.id)
                .where(CodigoBarras.negocio_id == negocio_id, CodigoBarras.codigo == codigo)
            )
        ).scalar_one_or_none()

    def guardar(self, codigo: CodigoBarras) -> None:
        self._s.add(codigo)

    async def borrar(self, codigo: CodigoBarras) -> None:
        await self._s.delete(codigo)
