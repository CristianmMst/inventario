from typing import Annotated

from fastapi import APIRouter, Depends, status
from sqlalchemy.ext.asyncio import AsyncSession

from app.esquemas.auth import Registro, Sesion
from app.infra.db import sesion as sesion_db
from app.servicios import auth as servicio

router = APIRouter(prefix="/auth", tags=["auth"])

SesionDb = Annotated[AsyncSession, Depends(sesion_db)]


@router.post("/registro", status_code=status.HTTP_201_CREATED, response_model=Sesion)
async def registro(datos: Registro, sesion: SesionDb) -> Sesion:
    """Crea usuario, negocio y membresía en una operación y abre sesión (RF-AUT-001)."""
    return await servicio.registrar(sesion, datos)
