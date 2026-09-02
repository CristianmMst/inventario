"""Casos de uso de identidad. El servicio es dueño de la transacción."""

from sqlalchemy.exc import IntegrityError
from sqlalchemy.ext.asyncio import AsyncSession

from app.dominio import errores as err
from app.esquemas.auth import Login, NegocioSalida, Registro, Sesion, UsuarioSalida
from app.infra import seguridad
from app.modelos.identidad import Negocio, Usuario
from app.repositorios.identidad import RepositorioIdentidad


def _sesion_de(usuario: Usuario, negocio: Negocio) -> Sesion:
    token, segundos = seguridad.crear_token_acceso(usuario.id, negocio.id)
    return Sesion(
        token_acceso=token,
        expira_en_segundos=segundos,
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
    except IntegrityError as e:
        # Un 409 escueto: no se revela nada más sobre la cuenta existente (RNF-12).
        raise err.Conflicto("CORREO_YA_REGISTRADO", "Ese correo ya tiene una cuenta.") from e
    return _sesion_de(usuario, negocio)


async def iniciar_sesion(sesion: AsyncSession, datos: Login) -> Sesion:
    """RF-AUT-002. Un correo inexistente y una contraseña incorrecta responden igual (RNF-11)."""
    repo = RepositorioIdentidad(sesion)
    usuario = await repo.usuario_por_email(str(datos.email))
    if usuario is None or not seguridad.verificar_contrasena(datos.password, usuario.password_hash):
        raise err.NoAutenticado("CREDENCIAL_INVALIDA", "Correo o contraseña incorrectos.")
    negocio = await repo.negocio_de_usuario(usuario.id)
    return _sesion_de(usuario, negocio)
