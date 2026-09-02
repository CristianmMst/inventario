from fastapi import APIRouter

from app.api.deps import Contexto, SesionDb
from app.esquemas.negocio import CredencialSalida, NegocioActual
from app.repositorios.identidad import RepositorioIdentidad

router = APIRouter(prefix="/negocio", tags=["negocio"])


@router.get("", response_model=NegocioActual)
async def negocio_actual(sesion: SesionDb, contexto: Contexto) -> NegocioActual:
    """El negocio de la credencial, con JWT o con X-API-Key (RF-AUT-004, RF-AUT-007)."""
    negocio = await RepositorioIdentidad(sesion).negocio_por_id(contexto.negocio_id)
    return NegocioActual(
        id=negocio.id,
        nombre=negocio.nombre,
        moneda_base=negocio.moneda_base,
        zona_horaria=negocio.zona_horaria,
        credencial=CredencialSalida(tipo=contexto.autor.tipo, id=contexto.autor.id),
    )
