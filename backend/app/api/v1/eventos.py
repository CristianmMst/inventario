from datetime import datetime
from typing import Annotated

from fastapi import APIRouter, Depends, Query

from app.api.deps import Contexto, SesionDb, paginacion
from app.esquemas.eventos import EventoSalida
from app.infra.paginacion import Pagina, ParametrosPagina
from app.servicios import eventos as servicio

router = APIRouter(prefix="/eventos", tags=["integracion"])


@router.get("", response_model=Pagina[EventoSalida])
async def listar(
    sesion: SesionDb,
    contexto: Contexto,
    pagina: Annotated[ParametrosPagina, Depends(paginacion)],
    desde_secuencia: Annotated[
        int | None, Query(ge=0, description="Última secuencia ya procesada (exclusiva)")
    ] = None,
    tipo: Annotated[str | None, Query(description="p. ej. stock.bajo_minimo")] = None,
    desde: Annotated[datetime | None, Query(description="ocurrido_en ≥, ISO 8601")] = None,
    hasta: Annotated[datetime | None, Query(description="ocurrido_en <, ISO 8601")] = None,
) -> Pagina[EventoSalida]:
    """Eventos de dominio en orden de ocurrencia, paginados por `secuencia` (RF-INT-004). Un
    consumidor se pone al día pidiendo `desde_secuencia` con la última que procesó."""
    return await servicio.listar(
        sesion,
        contexto.negocio_id,
        pagina,
        desde_secuencia=desde_secuencia,
        tipo=tipo,
        desde=desde,
        hasta=hasta,
    )
