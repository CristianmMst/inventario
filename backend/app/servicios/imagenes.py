"""Imágenes de producto y factura (RF-CAT-006, RF-FAC-005, RNF-05, RNF-11, RNF-16).

El servicio depende del Protocol `AlmacenImagenes` a través de la fábrica; nunca del
adaptador concreto. Nada se depura automáticamente: una imagen solo se borra cuando el
usuario reemplaza la foto de un producto o quita un adjunto de factura.
"""

import hashlib
import uuid
from datetime import timedelta

from sqlalchemy.ext.asyncio import AsyncSession

from app.almacenamiento import AlmacenImagenes, ImagenNoEncontrada, crear_almacen, generar_clave
from app.api.deps import ContextoNegocio
from app.config import obtener_ajustes
from app.dominio import errores as err
from app.dominio.imagenes import TipoImagen, validar_imagen
from app.esquemas.catalogo import ImagenSalida
from app.infra.firmas import firmar_lectura, verificar_token
from app.modelos.imagenes import Imagen
from app.repositorios.catalogo import RepositorioProductos
from app.repositorios.imagenes import RepositorioImagenes


def _almacen() -> AlmacenImagenes:
    return crear_almacen()


def _ttl() -> timedelta:
    return timedelta(minutes=obtener_ajustes().imagenes_url_minutos)


def identificador_de(clave: str) -> str:
    return hashlib.sha256(clave.encode()).hexdigest()[:32]


def a_salida(imagen: Imagen) -> ImagenSalida:
    return ImagenSalida(
        id=imagen.id,
        url=firmar_lectura(imagen.clave_almacenamiento, _ttl()),
        mime=imagen.mime,
        ancho=imagen.ancho,
        alto=imagen.alto,
        bytes=imagen.bytes,
    )


async def guardar_nueva(
    sesion: AsyncSession, negocio_id: uuid.UUID, contenido: bytes, tipo: TipoImagen
) -> Imagen:
    """Valida (RNF-05), guarda en el almacén con clave opaca y registra la fila. Debe llamarse
    dentro de la transacción del caso de uso."""
    info = validar_imagen(contenido, tipo)
    clave = generar_clave()
    await _almacen().guardar(clave, contenido, info.mime)
    imagen = Imagen(
        negocio_id=negocio_id,
        tipo=tipo.value,
        clave_almacenamiento=clave,
        identificador=identificador_de(clave),
        mime=info.mime,
        bytes=info.bytes,
        ancho=info.ancho,
        alto=info.alto,
        checksum=info.checksum,
    )
    RepositorioImagenes(sesion).guardar(imagen)
    await sesion.flush()
    await sesion.refresh(imagen)
    return imagen


async def borrar(sesion: AsyncSession, imagen: Imagen) -> None:
    """Quita la fila y el archivo. Solo por acción explícita del usuario (RNF-16)."""
    await RepositorioImagenes(sesion).borrar(imagen)
    await sesion.flush()
    await _almacen().borrar(imagen.clave_almacenamiento)


async def subir_imagen_producto(
    sesion: AsyncSession, contexto: ContextoNegocio, producto_id: uuid.UUID, contenido: bytes
) -> Imagen:
    """RF-CAT-006: una foto por producto; reemplazarla deja la anterior inaccesible."""
    async with sesion.begin():
        productos = RepositorioProductos(sesion)
        producto = await productos.por_id(contexto.negocio_id, producto_id)
        if producto is None:
            raise err.NoEncontrado("PRODUCTO_NO_ENCONTRADO", "Ese producto no existe.")
        anterior = producto.imagen
        nueva = await guardar_nueva(sesion, contexto.negocio_id, contenido, TipoImagen.PRODUCTO)
        producto.imagen_id = nueva.id
        await sesion.flush()
        if anterior is not None:
            await borrar(sesion, anterior)
        return nueva


async def url_firmada(sesion: AsyncSession, negocio_id: uuid.UUID, imagen_id: uuid.UUID) -> str:
    """GET /imagenes/{id} con credencial: redirige a la URL temporal firmada."""
    async with sesion.begin():
        imagen = await RepositorioImagenes(sesion).por_id(negocio_id, imagen_id)
        if imagen is None:
            raise err.NoEncontrado("IMAGEN_NO_ENCONTRADA", "Esa imagen no existe.")
        return firmar_lectura(imagen.clave_almacenamiento, _ttl())


async def leer_firmada(sesion: AsyncSession, identificador: str, token: str) -> tuple[bytes, str]:
    """GET /imagenes/{identificador}?t=…: sin credencial, solo con token válido y vigente. Un
    token malo responde lo mismo que una imagen inexistente (RNF-12)."""
    no_existe = err.NoEncontrado("IMAGEN_NO_ENCONTRADA", "Esa imagen no existe o el enlace caducó.")
    if not verificar_token(token, identificador):
        raise no_existe
    async with sesion.begin():
        imagen = await RepositorioImagenes(sesion).por_identificador(identificador)
        if imagen is None:
            raise no_existe
        try:
            contenido = await _almacen().leer(imagen.clave_almacenamiento)
        except ImagenNoEncontrada as e:
            raise no_existe from e
        return contenido, imagen.mime
