"""Contraseñas con Argon2id y JWT de acceso HS256 de vida corta (RNF-11, plan.md §5)."""

import uuid
from datetime import UTC, datetime, timedelta
from typing import Any

import jwt
from argon2 import PasswordHasher
from argon2.exceptions import InvalidHashError, VerifyMismatchError

from app.config import obtener_ajustes

_hasher = PasswordHasher()  # parámetros por defecto de argon2-cffi (RNF-11)

ALGORITMO_JWT = "HS256"


def hash_contrasena(contrasena: str) -> str:
    return _hasher.hash(contrasena)


def verificar_contrasena(contrasena: str, hash_: str) -> bool:
    try:
        return _hasher.verify(hash_, contrasena)
    except (VerifyMismatchError, InvalidHashError):
        return False


def hash_secreto(secreto: str) -> str:
    """Mismo esquema para refresh tokens y API keys: se guarda hash, nunca el valor."""
    return _hasher.hash(secreto)


def verificar_secreto(secreto: str, hash_: str) -> bool:
    return verificar_contrasena(secreto, hash_)


def crear_token_acceso(usuario_id: uuid.UUID, negocio_id: uuid.UUID) -> tuple[str, int]:
    """Devuelve el JWT y los segundos hasta su caducidad."""
    ajustes = obtener_ajustes()
    ahora = datetime.now(UTC)
    duracion = timedelta(minutes=ajustes.jwt_minutos_acceso)
    carga: dict[str, Any] = {
        "sub": str(usuario_id),
        "biz": str(negocio_id),
        "typ": "acceso",
        "jti": uuid.uuid4().hex,
        "iat": int(ahora.timestamp()),
        "exp": int((ahora + duracion).timestamp()),
    }
    return jwt.encode(carga, ajustes.jwt_secreto, algorithm=ALGORITMO_JWT), int(
        duracion.total_seconds()
    )


def decodificar_token_acceso(token: str) -> dict[str, Any]:
    """Lanza `jwt.PyJWTError` si el token no es válido o caducó."""
    carga: dict[str, Any] = jwt.decode(
        token,
        obtener_ajustes().jwt_secreto,
        algorithms=[ALGORITMO_JWT],
        options={"require": ["sub", "biz", "exp", "typ"]},
    )
    if carga.get("typ") != "acceso":
        raise jwt.InvalidTokenError("tipo de token incorrecto")
    return carga
