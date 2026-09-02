import uuid
from typing import Annotated

from fastapi import APIRouter, Depends, status

from app.api.deps import Contexto, SesionDb, paginacion
from app.esquemas.webhooks import SuscripcionNueva, SuscripcionSalida
from app.infra.paginacion import Pagina, ParametrosPagina
from app.servicios import webhooks as servicio

router = APIRouter(prefix="/webhooks", tags=["integracion"])

CONTRATO_ENTREGA = """Da de alta una suscripción de webhook saliente (RF-INT-005).

**Contrato de entrega (RF-INT-006, RF-INT-007), definido en v1 y aún no implementado: en v1
la suscripción se persiste y la entrega no se implementa.** Cuando exista, cada entrega será
un `POST` a la URL con el sobre completo del evento como cuerpo JSON y estas cabeceras:

- `X-Evento-Id`: identificador del evento, para que el receptor descarte duplicados.
- `X-Evento-Tipo`: p. ej. `stock.bajo_minimo`.
- `X-Evento-Momento`: instante de la firma, ISO 8601 UTC.
- `X-Evento-Firma`: `sha256=<hex>`, HMAC-SHA256 del cuerpo más el momento, con el secreto de
  la suscripción, para que el receptor verifique origen e integridad.

La semántica es **al menos una vez**: ante fallo o respuesta distinta de 2xx se reintenta con
espera creciente, y la suscripción se desactiva tras fallos persistentes. El receptor debe ser
idempotente por `X-Evento-Id`."""


@router.get("", response_model=Pagina[SuscripcionSalida])
async def listar(
    sesion: SesionDb,
    contexto: Contexto,
    pagina: Annotated[ParametrosPagina, Depends(paginacion)],
) -> Pagina[SuscripcionSalida]:
    return await servicio.listar(sesion, contexto.negocio_id, pagina)


@router.post(
    "",
    status_code=status.HTTP_201_CREATED,
    response_model=SuscripcionSalida,
    description=CONTRATO_ENTREGA,
)
async def crear(datos: SuscripcionNueva, sesion: SesionDb, contexto: Contexto) -> SuscripcionSalida:
    return await servicio.crear(sesion, contexto.negocio_id, datos)


@router.delete("/{suscripcion_id}", status_code=status.HTTP_204_NO_CONTENT)
async def eliminar(suscripcion_id: uuid.UUID, sesion: SesionDb, contexto: Contexto) -> None:
    await servicio.eliminar(sesion, contexto.negocio_id, suscripcion_id)
