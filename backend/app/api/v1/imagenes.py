import uuid
from typing import Annotated

from fastapi import APIRouter, File, Query, Request, UploadFile
from starlette.responses import RedirectResponse, Response

from app.api.deps import Contexto, SesionDb, contexto_actual
from app.dominio import errores as err
from app.esquemas.catalogo import ProductoSalida
from app.servicios import imagenes as servicio
from app.servicios import productos as servicio_productos

router = APIRouter(tags=["imagenes"])

# Un archivo de factura pesa como mucho 1,5 MB (RNF-05); se corta antes de leerlo entero.
MAX_SUBIDA = 2 * 1024 * 1024


async def _leer_subida(archivo: UploadFile) -> bytes:
    contenido = await archivo.read(MAX_SUBIDA + 1)
    return contenido


@router.put("/productos/{producto_id}/imagen", response_model=ProductoSalida)
async def subir_imagen_producto(
    producto_id: uuid.UUID,
    sesion: SesionDb,
    contexto: Contexto,
    archivo: Annotated[UploadFile, File(description="JPEG, PNG o WebP; ≤ 300 KB y ≤ 1280 px")],
) -> ProductoSalida:
    """Sube o reemplaza la foto del producto (RF-CAT-006). El servidor verifica los límites de
    RNF-05 sobre el contenido real; la anterior deja de estar accesible."""
    contenido = await _leer_subida(archivo)
    await servicio.subir_imagen_producto(sesion, contexto, producto_id, contenido)
    return await servicio_productos.obtener(sesion, contexto.negocio_id, producto_id)


@router.get(
    "/imagenes/{identificador}",
    response_class=Response,
    responses={
        307: {"description": "Con credencial y sin `t`: redirige a la URL firmada temporal."},
        200: {"content": {"image/jpeg": {}, "image/png": {}, "image/webp": {}}},
        404: {"description": "No existe, el token no es válido o el enlace caducó."},
    },
)
async def leer_imagen(
    identificador: str,
    request: Request,
    sesion: SesionDb,
    t: Annotated[str | None, Query(description="Token HMAC de la URL firmada")] = None,
) -> Response:
    """Dos modos. Sin `t`: exige credencial, toma `identificador` como id de imagen y redirige
    a la URL firmada (caduca a los 15 minutos). Con `t`: sirve los bytes sin credencial si el
    token es válido y vigente (RNF-11)."""
    if t is not None:
        contenido, mime = await servicio.leer_firmada(sesion, identificador, t)
        return Response(
            content=contenido, media_type=mime, headers={"Cache-Control": "private, max-age=300"}
        )
    contexto = await contexto_actual(
        sesion,
        request.headers.get("authorization"),
        request.headers.get("x-api-key"),
    )
    try:
        imagen_id = uuid.UUID(identificador)
    except ValueError as e:
        raise err.NoEncontrado("IMAGEN_NO_ENCONTRADA", "Esa imagen no existe.") from e
    destino = await servicio.url_firmada(sesion, contexto.negocio_id, imagen_id)
    return RedirectResponse(url=destino, status_code=307)
