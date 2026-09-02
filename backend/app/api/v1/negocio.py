from fastapi import APIRouter

from app.api.deps import Contexto, SesionDb
from app.esquemas.negocio import CredencialSalida, NegocioActual, NegocioEdicion
from app.repositorios.identidad import RepositorioIdentidad
from app.servicios import negocio as servicio_negocio

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


@router.patch("", response_model=NegocioActual)
async def editar_negocio(
    datos: NegocioEdicion, sesion: SesionDb, contexto: Contexto
) -> NegocioActual:
    """Edita nombre, zona horaria o moneda base. La moneda base responde 409 en cuanto existe
    un documento valorizado (RF-AUT-004, RN-10)."""
    negocio = await servicio_negocio.editar(sesion, contexto.negocio_id, datos)
    return NegocioActual(
        id=negocio.id,
        nombre=negocio.nombre,
        moneda_base=negocio.moneda_base,
        zona_horaria=negocio.zona_horaria,
        credencial=CredencialSalida(tipo=contexto.autor.tipo, id=contexto.autor.id),
    )
