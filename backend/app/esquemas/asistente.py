from typing import Any

from pydantic import BaseModel, Field


class ChatCreado(BaseModel):
    chat_id: str = Field(examples=["Jabr9TXYYJHfvl6Syypi88rdAHYHmcq6"])


class MensajeEntrada(BaseModel):
    contenido: str = Field(
        min_length=1, max_length=2000, examples=["¿Qué productos están por agotarse?"]
    )


class RespuestaAsistente(BaseModel):
    """Lo que el asistente le contesta al usuario, en orden. Puede venir más de un mensaje."""

    mensajes: list[str]


class LlamadaHerramienta(BaseModel):
    """Cuerpo con que Retell llama a una herramienta: `name`, `args` y el objeto de la
    conversación (`chat` en agentes de chat; `call` en agentes de voz). Solo se lee el id."""

    name: str | None = None
    args: dict[str, Any] = Field(default_factory=dict)
    chat: dict[str, Any] | None = None
    call: dict[str, Any] | None = None

    def chat_id(self) -> str | None:
        for objeto in (self.chat, self.call):
            if objeto and objeto.get("chat_id"):
                return str(objeto["chat_id"])
        return None


class ProductoParaAsistente(BaseModel):
    nombre: str
    sku: str
    unidad: str
    stock_actual: str
    stock_minimo: str | None
    estado_stock: str = Field(description="agotado, bajo_minimo u optimo")


class ResultadoHerramienta(BaseModel):
    """Respuesta compacta para el modelo: Retell corta a 15.000 caracteres."""

    total: int
    hay_mas: bool
    productos: list[ProductoParaAsistente]
