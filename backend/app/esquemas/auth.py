import uuid
from typing import Annotated

from pydantic import BaseModel, EmailStr, Field, StringConstraints

CodigoMoneda = Annotated[str, StringConstraints(pattern=r"^[A-Z]{3}$")]
Contrasena = Annotated[str, Field(min_length=8, max_length=256)]


class NegocioNuevo(BaseModel):
    nombre: Annotated[str, Field(min_length=1, max_length=200)]
    moneda_base: CodigoMoneda
    zona_horaria: Annotated[str, Field(min_length=1, max_length=64)] = "UTC"


class Registro(BaseModel):
    email: EmailStr
    password: Contrasena
    nombre: Annotated[str, Field(min_length=1, max_length=200)]
    negocio: NegocioNuevo


class Login(BaseModel):
    email: EmailStr
    password: Contrasena


class TokenRenovacion(BaseModel):
    token_renovacion: Annotated[str, Field(min_length=20, max_length=512)]


class NegocioSalida(BaseModel):
    id: uuid.UUID
    nombre: str
    moneda_base: str
    zona_horaria: str


class UsuarioSalida(BaseModel):
    id: uuid.UUID
    email: str
    nombre: str


class Sesion(BaseModel):
    token_acceso: str
    tipo: str = "Bearer"
    expira_en_segundos: int
    token_renovacion: str
    usuario: UsuarioSalida
    negocio: NegocioSalida


class CambioContrasena(BaseModel):
    password_actual: Contrasena
    password_nueva: Contrasena
