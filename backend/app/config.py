from functools import lru_cache
from pathlib import Path
from typing import Literal

from pydantic import Field, model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict

SECRETO_DE_DESARROLLO = "solo-para-desarrollo-cambiar-en-produccion"


class Ajustes(BaseSettings):
    """Configuración por entorno. Se lee de variables de entorno o de `.env`."""

    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    entorno: Literal["desarrollo", "pruebas", "produccion"] = "desarrollo"
    database_url: str = "postgresql+asyncpg://inventario:inventario@localhost:5432/inventario"
    jwt_secreto: str = Field(SECRETO_DE_DESARROLLO, min_length=32)
    jwt_minutos_acceso: int = 15
    refresh_dias: int = 60
    imagenes_dir: Path = Path("./datos/imagenes")
    imagenes_secreto: str = Field(SECRETO_DE_DESARROLLO, min_length=32)
    imagenes_url_minutos: int = 15
    log_json: bool = True
    # Asistente (RF-AST): la API key de Retell vive solo aquí, nunca en la app.
    retell_api_key: str = ""
    retell_agent_id: str = ""
    retell_url_base: str = "https://api.retellai.com"
    url_publica_api: str = "https://api.mikelabs.com.co"

    @model_validator(mode="after")
    def _secretos_reales_en_produccion(self) -> "Ajustes":
        if self.entorno == "produccion":
            por_defecto = [
                nombre
                for nombre in ("jwt_secreto", "imagenes_secreto")
                if getattr(self, nombre) == SECRETO_DE_DESARROLLO
            ]
            if por_defecto:
                raise ValueError(f"en producción hay que definir {', '.join(por_defecto)}")
        return self


@lru_cache
def obtener_ajustes() -> Ajustes:
    return Ajustes()
