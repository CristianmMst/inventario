"""Asistente de inventario sobre Retell (RF-AST-001..004).

La app crea un chat y conversa a través de nuestra API; Retell responde y, cuando necesita
datos, llama a las herramientas de abajo. Cada chat queda atado al negocio que lo creó, y las
herramientas solo devuelven datos de ese negocio (RN-19)."""

import uuid
from decimal import Decimal
from typing import Literal

from sqlalchemy.ext.asyncio import AsyncSession

from app.dominio import errores as err
from app.esquemas.asistente import (
    ChatCreado,
    ProductoParaAsistente,
    RespuestaAsistente,
    ResultadoHerramienta,
)
from app.esquemas.catalogo import ProductoSalida
from app.infra.paginacion import ParametrosPagina
from app.infra.retell import ClienteRetell
from app.modelos.asistente import ChatAsistente
from app.repositorios.asistente import RepositorioAsistente
from app.servicios import productos, reportes

LIMITE_HERRAMIENTA = 30


def _chat_no_encontrado() -> err.NoEncontrado:
    return err.NoEncontrado("CHAT_NO_ENCONTRADO", "Esa conversación ya no existe.")


async def crear_chat(
    sesion: AsyncSession,
    retell: ClienteRetell,
    negocio_id: uuid.UUID,
    autor_tipo: Literal["usuario", "servicio"],
    autor_id: uuid.UUID,
) -> ChatCreado:
    chat_id = await retell.crear_chat({"negocio_id": str(negocio_id)})
    async with sesion.begin():
        RepositorioAsistente(sesion).guardar(
            ChatAsistente(
                chat_id=chat_id, negocio_id=negocio_id, autor_tipo=autor_tipo, autor_id=autor_id
            )
        )
    return ChatCreado(chat_id=chat_id)


async def _chat_activo(sesion: AsyncSession, negocio_id: uuid.UUID, chat_id: str) -> None:
    async with sesion.begin():
        chat = await RepositorioAsistente(sesion).del_negocio(negocio_id, chat_id)
    if chat is None:
        raise _chat_no_encontrado()
    if chat.terminado_en is not None:
        raise err.Conflicto("CHAT_TERMINADO", "Esa conversación ya terminó. Empieza otra.")


async def conversar(
    sesion: AsyncSession,
    retell: ClienteRetell,
    negocio_id: uuid.UUID,
    chat_id: str,
    contenido: str,
) -> RespuestaAsistente:
    await _chat_activo(sesion, negocio_id, chat_id)
    return RespuestaAsistente(mensajes=await retell.completar(chat_id, contenido.strip()))


async def terminar(
    sesion: AsyncSession, retell: ClienteRetell, negocio_id: uuid.UUID, chat_id: str
) -> None:
    async with sesion.begin():
        repo = RepositorioAsistente(sesion)
        chat = await repo.del_negocio(negocio_id, chat_id)
        if chat is None:
            raise _chat_no_encontrado()
        if chat.terminado_en is not None:
            return
        await repo.marcar_terminado(chat_id)
    await retell.terminar_chat(chat_id)


async def negocio_del_chat(sesion: AsyncSession, chat_id: str | None) -> uuid.UUID:
    """Para las herramientas: el chat lo dice Retell (con firma); el negocio lo decimos
    nosotros. Un chat que no creamos, o ya terminado, no ve nada."""
    if not chat_id:
        raise _chat_no_encontrado()
    async with sesion.begin():
        chat = await RepositorioAsistente(sesion).por_chat_id(chat_id)
    if chat is None or chat.terminado_en is not None:
        raise _chat_no_encontrado()
    return chat.negocio_id


def _estado(stock: str, minimo: str | None) -> str:
    actual = Decimal(stock)
    if actual <= 0:
        return "agotado"
    if minimo is not None and actual <= Decimal(minimo):
        return "bajo_minimo"
    return "optimo"


def _de_producto(p: ProductoSalida) -> ProductoParaAsistente:
    return ProductoParaAsistente(
        nombre=p.nombre,
        sku=p.sku,
        unidad=p.unidad.codigo,
        stock_actual=p.stock_actual,
        stock_minimo=p.stock_minimo,
        estado_stock=_estado(p.stock_actual, p.stock_minimo),
    )


async def buscar_producto(
    sesion: AsyncSession, negocio_id: uuid.UUID, texto: str
) -> ResultadoHerramienta:
    """Reutiliza la búsqueda del catálogo (RF-CAT-007): nombre, SKU o código."""
    pagina = await productos.buscar(
        sesion, negocio_id, texto, ParametrosPagina(limit=LIMITE_HERRAMIENTA)
    )
    activos = [_de_producto(p) for p in pagina.datos if p.estado == "activo"]
    return ResultadoHerramienta(total=len(activos), hay_mas=pagina.tiene_mas, productos=activos)


async def stock_bajo(sesion: AsyncSession, negocio_id: uuid.UUID) -> ResultadoHerramienta:
    """RF-REP-001, el más urgente primero."""
    lista = await reportes.bajo_minimo(
        sesion, negocio_id, ParametrosPagina(limit=LIMITE_HERRAMIENTA)
    )
    return ResultadoHerramienta(
        total=len(lista.datos),
        hay_mas=lista.tiene_mas,
        productos=[
            ProductoParaAsistente(
                nombre=f.producto.nombre,
                sku=f.producto.sku,
                unidad=f.producto.unidad_codigo,
                stock_actual=f.stock_actual,
                stock_minimo=f.stock_minimo,
                estado_stock=_estado(f.stock_actual, f.stock_minimo),
            )
            for f in lista.datos
        ],
    )


async def agotados(sesion: AsyncSession, negocio_id: uuid.UUID) -> ResultadoHerramienta:
    """RF-REP-007."""
    lista = await reportes.agotados(sesion, negocio_id, ParametrosPagina(limit=LIMITE_HERRAMIENTA))
    return ResultadoHerramienta(
        total=len(lista.datos),
        hay_mas=lista.tiene_mas,
        productos=[
            ProductoParaAsistente(
                nombre=f.producto.nombre,
                sku=f.producto.sku,
                unidad=f.producto.unidad_codigo,
                stock_actual=f.stock_actual,
                stock_minimo=f.stock_minimo,
                estado_stock="agotado",
            )
            for f in lista.datos
        ],
    )
