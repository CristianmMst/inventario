import uuid

from fastapi import APIRouter, Request, status
from starlette.responses import JSONResponse

from app.api.deps import Contexto, SesionDb
from app.esquemas.inventario import ConteoEntrada, ConteoSalida
from app.infra.idempotencia import ClaveIdempotencia, ejecutar_idempotente
from app.servicios import movimientos as servicio

router = APIRouter(prefix="/productos", tags=["inventario"])


@router.post("/{producto_id}/conteo", response_model=ConteoSalida)
async def conteo(
    producto_id: uuid.UUID,
    datos: ConteoEntrada,
    request: Request,
    sesion: SesionDb,
    contexto: Contexto,
    clave: ClaveIdempotencia,
) -> JSONResponse:
    """Ajuste por conteo físico (RF-INV-013): se declara la cantidad contada y el servidor
    calcula y registra la diferencia. Si la diferencia es cero, `movimiento` es null."""

    async def operacion() -> ConteoSalida:
        return await servicio.contar(sesion, contexto, producto_id, datos)

    return await ejecutar_idempotente(
        sesion, contexto, clave, request, status.HTTP_200_OK, operacion
    )
