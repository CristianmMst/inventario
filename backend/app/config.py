from functools import lru_cache
from pathlib import Path
from typing import Literal

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Ajustes(BaseSettings):
    """Configuración por entorno. Se lee de variables de entorno o de `.env`."""

    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    entorno: Literal["desarrollo", "pruebas", "produccion"] = "desarrollo"
    database_url: str = "postgresql+asyncpg://inventario:inventario@localhost:5432/inventario"
    jwt_secreto: str = Field("solo-para-desarrollo-cambiar-en-produccion", min_length=32)
    jwt_minutos_acceso: int = 15
    refresh_dias: int = 60
    imagenes_dir: Path = Path("./datos/imagenes")
    imagenes_secreto: str = Field("solo-para-desarrollo-cambiar-en-produccion", min_length=32)
    imagenes_url_minutos: int = 15
    log_json: bool = True


@lru_cache
def obtener_ajustes() -> Ajustes:
    return Ajustes()
