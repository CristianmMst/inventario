import uuid
from datetime import date
from typing import Annotated

from fastapi import APIRouter, Depends, File, Query, Request, UploadFile, status
from starlette.responses import JSONResponse, Response

from app.api.deps import Contexto, SesionDb, paginacion
from app.esquemas.facturas import (
    AnulacionFactura,
    EstadoPago,
    FacturaEdicion,
    FacturaNueva,
    FacturaSalida,
    PaginaFacturas,
    PagoEntrada,
    RecepcionesVinculacion,
)
from app.infra.idempotencia import ClaveIdempotencia, ejecutar_idempotente
from app.infra.paginacion import ParametrosPagina
from app.servicios import exportacion
from app.servicios import facturas as servicio

router = APIRouter(prefix="/facturas", tags=["facturas"])


@router.get("", response_model=PaginaFacturas)
async def listar(
    sesion: SesionDb,
    contexto: Contexto,
    pagina: Annotated[ParametrosPagina, Depends(paginacion)],
    proveedor_id: Annotated[uuid.UUID | None, Query()] = None,
    estado_pago: Annotated[EstadoPago | None, Query()] = None,
    desde: Annotated[date | None, Query(description="Fecha de emisión, inclusive")] = None,
    hasta: Annotated[date | None, Query(description="Fecha de emisión, inclusive")] = None,
) -> PaginaFacturas:
    """Listado paginado con filtros y total acumulado del filtro aplicado (RF-FAC-008)."""
    return await servicio.listar(
        sesion,
        contexto.negocio_id,
        pagina,
        proveedor_id=proveedor_id,
        estado_pago=estado_pago,
        desde=desde,
        hasta=hasta,
    )


@router.post(
    "",
    status_code=status.HTTP_201_CREATED,
    response_model=FacturaSalida,
    responses={
        409: {
            "description": "FACTURA_DUPLICADA: mismo número para el mismo proveedor (RF-FAC-002)."
        },
        422: {
            "description": "FACTURA_NO_CUADRA: base + impuesto ≠ total; `details.diferencia` "
            "muestra cuánto falta o sobra (RN-18)."
        },
    },
)
async def crear(
    datos: FacturaNueva,
    request: Request,
    sesion: SesionDb,
    contexto: Contexto,
    clave: ClaveIdempotencia,
) -> JSONResponse:
    """Registra una factura de compra (RF-FAC-001). Exige Idempotency-Key."""

    async def operacion() -> FacturaSalida:
        return await servicio.crear(sesion, contexto, datos)

    return await ejecutar_idempotente(
        sesion, contexto, clave, request, status.HTTP_201_CREATED, operacion
    )


@router.get(
    "/exportacion",
    response_class=Response,
    responses={
        200: {
            "content": {"application/zip": {}},
            "description": "ZIP con facturas.csv y las imágenes del período",
        }
    },
)
async def exportar(
    desde: Annotated[date, Query(description="Fecha de emisión inicial, inclusive")],
    hasta: Annotated[date, Query(description="Fecha de emisión final, inclusive")],
    sesion: SesionDb,
    contexto: Contexto,
) -> Response:
    """Exporta las facturas del rango como un ZIP autocontenido: CSV con todos los datos e
    imágenes nombradas AAAA-MM-DD_proveedor_numero.jpg (RF-FAC-007). Sin caducidad."""
    contenido = await exportacion.exportar_facturas(sesion, contexto.negocio_id, desde, hasta)
    nombre = f"facturas_{desde.isoformat()}_{hasta.isoformat()}.zip"
    return Response(
        content=contenido,
        media_type="application/zip",
        headers={"Content-Disposition": f'attachment; filename="{nombre}"'},
    )


@router.get("/{factura_id}", response_model=FacturaSalida)
async def ficha(factura_id: uuid.UUID, sesion: SesionDb, contexto: Contexto) -> FacturaSalida:
    return await servicio.obtener(sesion, contexto.negocio_id, factura_id)


@router.patch("/{factura_id}", response_model=FacturaSalida)
async def editar(
    factura_id: uuid.UUID, datos: FacturaEdicion, sesion: SesionDb, contexto: Contexto
) -> FacturaSalida:
    """Edita notas y fecha de vencimiento. Los importes no se editan: se anula y se registra."""
    return await servicio.editar(sesion, contexto.negocio_id, factura_id, datos)


@router.post("/{factura_id}/pagar", response_model=FacturaSalida)
async def pagar(
    factura_id: uuid.UUID, datos: PagoEntrada, sesion: SesionDb, contexto: Contexto
) -> FacturaSalida:
    """Marca la factura como pagada con fecha de pago (RF-FAC-004)."""
    return await servicio.pagar(sesion, contexto, factura_id, datos)


@router.post("/{factura_id}/anular", response_model=FacturaSalida)
async def anular(
    factura_id: uuid.UUID, datos: AnulacionFactura, sesion: SesionDb, contexto: Contexto
) -> FacturaSalida:
    """Anula la factura indicando el motivo (RF-FAC-004)."""
    return await servicio.anular(sesion, contexto.negocio_id, factura_id, datos)


@router.put("/{factura_id}/recepciones", response_model=FacturaSalida)
async def vincular_recepciones(
    factura_id: uuid.UUID, datos: RecepcionesVinculacion, sesion: SesionDb, contexto: Contexto
) -> FacturaSalida:
    """Sustituye las recepciones vinculadas: del mismo proveedor, confirmadas, y cada una en una
    sola factura (RF-FAC-006)."""
    return await servicio.vincular_recepciones(sesion, contexto.negocio_id, factura_id, datos)


@router.post(
    "/{factura_id}/imagenes", status_code=status.HTTP_201_CREATED, response_model=FacturaSalida
)
async def adjuntar_imagen(
    factura_id: uuid.UUID,
    sesion: SesionDb,
    contexto: Contexto,
    archivo: Annotated[UploadFile, File(description="JPEG, PNG o WebP; ≤ 1,5 MB y ≤ 2048 px")],
) -> FacturaSalida:
    """Adjunta una imagen del documento (RF-FAC-005). Varias por factura, ordenadas."""
    contenido = await archivo.read(2 * 1024 * 1024 + 1)
    return await servicio.adjuntar_imagen(sesion, contexto.negocio_id, factura_id, contenido)


@router.delete("/{factura_id}/imagenes/{imagen_id}", response_model=FacturaSalida)
async def quitar_imagen(
    factura_id: uuid.UUID, imagen_id: uuid.UUID, sesion: SesionDb, contexto: Contexto
) -> FacturaSalida:
    return await servicio.quitar_imagen(sesion, contexto.negocio_id, factura_id, imagen_id)
