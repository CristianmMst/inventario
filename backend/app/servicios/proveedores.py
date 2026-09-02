"""RF-COM-001 y RN-17: proveedores. Con documentos asociados no se borran, se archivan."""

import uuid

from sqlalchemy.ext.asyncio import AsyncSession

from app.api.deps import ContextoNegocio
from app.dominio import errores as err
from app.dominio import eventos as ev
from app.esquemas.compras import ProveedorEdicion, ProveedorNuevo, ProveedorSalida
from app.infra.paginacion import Pagina, ParametrosPagina, decodificar_cursor, paginar
from app.modelos.compras import Proveedor
from app.repositorios.compras import RepositorioProveedores
from app.servicios.eventos import emitir


def _salida(p: Proveedor) -> ProveedorSalida:
    return ProveedorSalida(
        id=p.id,
        nombre=p.nombre,
        identificacion_fiscal=p.identificacion_fiscal,
        contacto=p.contacto,
        telefono=p.telefono,
        email=p.email,
        direccion=p.direccion,
        notas=p.notas,
        estado=p.estado,  # type: ignore[arg-type]
    )


def no_encontrado() -> err.NoEncontrado:
    return err.NoEncontrado("PROVEEDOR_NO_ENCONTRADO", "Ese proveedor no existe.")


async def _cargar(
    sesion: AsyncSession, negocio_id: uuid.UUID, proveedor_id: uuid.UUID
) -> Proveedor:
    proveedor = await RepositorioProveedores(sesion).por_id(negocio_id, proveedor_id)
    if proveedor is None:
        raise no_encontrado()
    return proveedor


async def proveedor_seleccionable(
    sesion: AsyncSession, negocio_id: uuid.UUID, proveedor_id: uuid.UUID
) -> Proveedor:
    """Para documentos nuevos: existe y está activo. Un archivado no es seleccionable."""
    proveedor = await _cargar(sesion, negocio_id, proveedor_id)
    if proveedor.estado != "activo":
        raise err.ValidacionInvalida(
            "PROVEEDOR_ARCHIVADO",
            f"«{proveedor.nombre}» está archivado; desarchívalo para usarlo en documentos nuevos.",
            {"proveedor_id": str(proveedor.id)},
        )
    return proveedor


async def crear(
    sesion: AsyncSession, contexto: ContextoNegocio, datos: ProveedorNuevo
) -> ProveedorSalida:
    proveedor = Proveedor(negocio_id=contexto.negocio_id, **datos.model_dump())
    async with sesion.begin():
        RepositorioProveedores(sesion).guardar(proveedor)
        await sesion.flush()
        await sesion.refresh(proveedor)
        await emitir(
            sesion,
            contexto,
            ev.proveedor_creado,
            proveedor_id=proveedor.id,
            nombre=proveedor.nombre,
            identificacion_fiscal=proveedor.identificacion_fiscal,
            contacto=proveedor.contacto,
        )
    return _salida(proveedor)


async def obtener(
    sesion: AsyncSession, negocio_id: uuid.UUID, proveedor_id: uuid.UUID
) -> ProveedorSalida:
    async with sesion.begin():
        return _salida(await _cargar(sesion, negocio_id, proveedor_id))


async def editar(
    sesion: AsyncSession, negocio_id: uuid.UUID, proveedor_id: uuid.UUID, datos: ProveedorEdicion
) -> ProveedorSalida:
    async with sesion.begin():
        proveedor = await _cargar(sesion, negocio_id, proveedor_id)
        for campo in datos.model_fields_set:
            valor = getattr(datos, campo)
            if campo == "nombre" and valor is None:
                continue
            setattr(proveedor, campo, valor)
        await sesion.flush()
        await sesion.refresh(proveedor)
        return _salida(proveedor)


async def listar(
    sesion: AsyncSession, negocio_id: uuid.UUID, pagina: ParametrosPagina, *, estado: str
) -> Pagina[ProveedorSalida]:
    despues = None
    if pagina.cursor:
        c = decodificar_cursor(pagina.cursor)
        despues = (str(c["n"]), uuid.UUID(str(c["id"])))
    async with sesion.begin():
        filas = await RepositorioProveedores(sesion).listar(
            negocio_id,
            estado=None if estado == "todos" else estado,
            limite=pagina.limit + 1,
            despues_de=despues,
        )
    return paginar(
        [_salida(p) for p in filas],
        pagina.limit,
        clave_de=lambda s: {"n": s.nombre, "id": str(s.id)},
    )


async def _cambiar_estado(
    sesion: AsyncSession, negocio_id: uuid.UUID, proveedor_id: uuid.UUID, estado: str
) -> ProveedorSalida:
    async with sesion.begin():
        proveedor = await _cargar(sesion, negocio_id, proveedor_id)
        proveedor.estado = estado
        await sesion.flush()
        await sesion.refresh(proveedor)
        return _salida(proveedor)


async def archivar(
    sesion: AsyncSession, negocio_id: uuid.UUID, proveedor_id: uuid.UUID
) -> ProveedorSalida:
    return await _cambiar_estado(sesion, negocio_id, proveedor_id, "archivado")


async def desarchivar(
    sesion: AsyncSession, negocio_id: uuid.UUID, proveedor_id: uuid.UUID
) -> ProveedorSalida:
    return await _cambiar_estado(sesion, negocio_id, proveedor_id, "activo")


async def eliminar(sesion: AsyncSession, negocio_id: uuid.UUID, proveedor_id: uuid.UUID) -> None:
    """RN-17: solo se borra un proveedor sin documentos; con documentos, 409 ofreciendo archivar."""
    repo = RepositorioProveedores(sesion)
    async with sesion.begin():
        proveedor = await _cargar(sesion, negocio_id, proveedor_id)
        if await repo.tiene_documentos(proveedor.id):
            raise err.Conflicto(
                "PROVEEDOR_CON_DOCUMENTOS",
                f"«{proveedor.nombre}» tiene órdenes, recepciones o facturas. Archívalo en vez de "
                "borrarlo: su historial se conserva.",
                {"proveedor_id": str(proveedor.id), "accion_sugerida": "archivar"},
            )
        await repo.borrar(proveedor)
