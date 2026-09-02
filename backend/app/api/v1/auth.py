from fastapi import APIRouter, status

from app.api.deps import Contexto, SesionDb
from app.esquemas.auth import CambioContrasena, Login, Registro, Sesion, TokenRenovacion
from app.servicios import auth as servicio

router = APIRouter(prefix="/auth", tags=["auth"])


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


@router.patch("/password", status_code=status.HTTP_204_NO_CONTENT)
async def cambiar_password(datos: CambioContrasena, sesion: SesionDb, contexto: Contexto) -> None:
    """Cambia la contraseña del propio usuario y revoca sus tokens de renovación (RF-AUT-006)."""
    await servicio.cambiar_contrasena(sesion, contexto.autor.id, datos)
