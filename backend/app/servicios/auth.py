"""Casos de uso de identidad. El servicio es dueño de la transacción."""

import uuid
from datetime import UTC, datetime, timedelta

from sqlalchemy.exc import IntegrityError
from sqlalchemy.ext.asyncio import AsyncSession

from app.config import obtener_ajustes
from app.dominio import errores as err
from app.esquemas.auth import (
    Login,
    NegocioSalida,
    Registro,
    Sesion,
    TokenRenovacion,
    UsuarioSalida,
)
from app.infra import seguridad
from app.modelos.identidad import Negocio, RefreshToken, Usuario
from app.repositorios.identidad import RepositorioIdentidad


def _credencial_invalida() -> err.NoAutenticado:
    return err.NoAutenticado(
        "CREDENCIAL_INVALIDA", "La sesión no es válida. Vuelve a iniciar sesión."
    )


def _emitir_refresh(
    repo: RepositorioIdentidad, usuario_id: uuid.UUID, familia_id: uuid.UUID
) -> str:
    token = seguridad.generar_token_opaco()
    repo.guardar_refresh(
        RefreshToken(
            usuario_id=usuario_id,
            token_hash=seguridad.hash_token_opaco(token),
            familia_id=familia_id,
            expira_en=datetime.now(UTC) + timedelta(days=obtener_ajustes().refresh_dias),
        )
    )
    return token


def _sesion_de(usuario: Usuario, negocio: Negocio, token_renovacion: str) -> Sesion:
    token, segundos = seguridad.crear_token_acceso(usuario.id, negocio.id)
    return Sesion(
        token_acceso=token,
        expira_en_segundos=segundos,
        token_renovacion=token_renovacion,
        usuario=UsuarioSalida(id=usuario.id, email=usuario.email, nombre=usuario.nombre),
        negocio=NegocioSalida(
            id=negocio.id,
            nombre=negocio.nombre,
            moneda_base=negocio.moneda_base,
            zona_horaria=negocio.zona_horaria,
        ),
    )


async def registrar(sesion: AsyncSession, datos: Registro) -> Sesion:
    """RF-AUT-001 / RF-AUT-004: usuario, negocio y membresía en una sola transacción."""
    repo = RepositorioIdentidad(sesion)
    usuario = Usuario(
        email=str(datos.email),
        password_hash=seguridad.hash_contrasena(datos.password),
        nombre=datos.nombre,
    )
    negocio = Negocio(
        nombre=datos.negocio.nombre,
        moneda_base=datos.negocio.moneda_base,
        zona_horaria=datos.negocio.zona_horaria,
    )
    try:
        async with sesion.begin():
            await repo.crear_usuario_con_negocio(usuario, negocio, rol="dueno")
            renovacion = _emitir_refresh(repo, usuario.id, familia_id=uuid.uuid4())
    except IntegrityError as e:
        # Un 409 escueto: no se revela nada más sobre la cuenta existente (RNF-12).
        raise err.Conflicto("CORREO_YA_REGISTRADO", "Ese correo ya tiene una cuenta.") from e
    return _sesion_de(usuario, negocio, renovacion)


async def iniciar_sesion(sesion: AsyncSession, datos: Login) -> Sesion:
    """RF-AUT-002. Un correo inexistente y una contraseña incorrecta responden igual (RNF-11)."""
    repo = RepositorioIdentidad(sesion)
    async with sesion.begin():
        usuario = await repo.usuario_por_email(str(datos.email))
        if usuario is None or not seguridad.verificar_contrasena(
            datos.password, usuario.password_hash
        ):
            raise err.NoAutenticado("CREDENCIAL_INVALIDA", "Correo o contraseña incorrectos.")
        negocio = await repo.negocio_de_usuario(usuario.id)
        renovacion = _emitir_refresh(repo, usuario.id, familia_id=uuid.uuid4())
    return _sesion_de(usuario, negocio, renovacion)


async def renovar(sesion: AsyncSession, datos: TokenRenovacion) -> Sesion:
    """RF-AUT-003: rota el token de renovación. Reusar uno revocado revoca toda la familia."""
    repo = RepositorioIdentidad(sesion)
    resultado: Sesion | None = None
    async with sesion.begin():
        actual = await repo.refresh_por_hash(seguridad.hash_token_opaco(datos.token_renovacion))
        if actual is None:
            raise _credencial_invalida()
        if actual.revocado_en is not None:
            # Señal de que alguien copió el token: se corta toda la familia (RNF-11).
            # La revocación debe confirmarse, así que el error se lanza fuera del bloque.
            await repo.revocar_familia(actual.familia_id)
        elif actual.expira_en <= datetime.now(UTC):
            raise _credencial_invalida()
        else:
            actual.revocado_en = datetime.now(UTC)
            usuario = await repo.usuario_por_id(actual.usuario_id)
            if usuario is None:
                raise _credencial_invalida()
            negocio = await repo.negocio_de_usuario(usuario.id)
            nuevo = _emitir_refresh(repo, usuario.id, familia_id=actual.familia_id)
            resultado = _sesion_de(usuario, negocio, nuevo)
    if resultado is None:
        raise _credencial_invalida()
    return resultado


async def cerrar_sesion(sesion: AsyncSession, datos: TokenRenovacion) -> None:
    """Revoca el token de renovación entregado. Idempotente: uno desconocido no falla."""
    repo = RepositorioIdentidad(sesion)
    async with sesion.begin():
        actual = await repo.refresh_por_hash(seguridad.hash_token_opaco(datos.token_renovacion))
        if actual is not None and actual.revocado_en is None:
            actual.revocado_en = datetime.now(UTC)
