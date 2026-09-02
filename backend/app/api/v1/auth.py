from typing import Annotated

from fastapi import APIRouter, Depends, status
from sqlalchemy.ext.asyncio import AsyncSession

from app.esquemas.auth import Login, Registro, Sesion, TokenRenovacion
from app.infra.db import sesion as sesion_db
from app.servicios import auth as servicio

router = APIRouter(prefix="/auth", tags=["auth"])

SesionDb = Annotated[AsyncSession, Depends(sesion_db)]


@router.post("/registro", status_code=status.HTTP_201_CREATED, response_model=Sesion)
async def registro(datos: Registro, sesion: SesionDb) -> Sesion:
    """Crea usuario, negocio y membresía en una operación y abre sesión (RF-AUT-001)."""
    return await servicio.registrar(sesion, datos)


@router.post("/login", response_model=Sesion)
async def login(datos: Login, sesion: SesionDb) -> Sesion:
    """Inicio de sesión: token de acceso de vida corta y token de renovación (RF-AUT-002)."""
    return await servicio.iniciar_sesion(sesion, datos)


@router.post("/refresh", response_model=Sesion)
async def refresh(datos: TokenRenovacion, sesion: SesionDb) -> Sesion:
    """Renueva la sesión sin contraseña; el token de renovación rota en cada uso (RF-AUT-003)."""
    return await servicio.renovar(sesion, datos)


@router.post("/logout", status_code=status.HTTP_204_NO_CONTENT)
async def logout(datos: TokenRenovacion, sesion: SesionDb) -> None:
    """Revoca el token de renovación (RF-AUT-002)."""
    await servicio.cerrar_sesion(sesion, datos)
