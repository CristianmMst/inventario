from typing import Annotated

from fastapi import APIRouter, Depends, Query

from app.api.deps import Contexto, SesionDb, paginacion
from app.dominio.movimientos import TipoMovimiento
from app.esquemas.inventario import MotivoSalida
from app.infra.paginacion import Pagina, ParametrosPagina, paginar
from app.servicios import motivos as servicio

router = APIRouter(prefix="/motivos-movimiento", tags=["inventario"])


@router.get("", response_model=Pagina[MotivoSalida])
async def listar(
    sesion: SesionDb,
    _: Contexto,
    pagina: Annotated[ParametrosPagina, Depends(paginacion)],
    tipo: Annotated[TipoMovimiento | None, Query()] = None,
) -> Pagina[MotivoSalida]:
    """Lista cerrada de motivos por tipo (RF-INV-010). Cabe en una página: es una semilla."""
    motivos = await servicio.motivos_de(sesion, tipo)
    salida = [
        MotivoSalida(
            codigo=m.codigo,
            tipo_movimiento=m.tipo_movimiento,  # type: ignore[arg-type]
            etiqueta=m.etiqueta,
            exige_nota=m.exige_nota,
        )
        for m in motivos
    ]
    return paginar(salida, max(pagina.limit, len(salida)), clave_de=lambda m: {"c": m.codigo})
