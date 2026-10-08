from typing import Annotated, Literal

from fastapi import APIRouter, Depends, Header, Request, Response
from pydantic import ValidationError

from app.api.deps import Contexto, SesionDb
from app.dominio import errores as err
from app.esquemas.asistente import (
    ChatCreado,
    LlamadaHerramienta,
    MensajeEntrada,
    RespuestaAsistente,
    ResultadoHerramienta,
)
from app.infra.retell import ClienteRetell, cliente_retell
from app.servicios import asistente as servicio

router = APIRouter(prefix="/asistente", tags=["asistente"])

Retell = Annotated[ClienteRetell, Depends(cliente_retell)]


@router.post("/chats", response_model=ChatCreado, status_code=201)
async def crear_chat(sesion: SesionDb, contexto: Contexto, retell: Retell) -> ChatCreado:
    """Abre una conversación con el asistente de inventario (RF-AST-001). Si el servidor no
    tiene configurado Retell responde 503 `ASISTENTE_NO_CONFIGURADO`."""
    return await servicio.crear_chat(
        sesion, retell, contexto.negocio_id, contexto.autor.tipo, contexto.autor.id
    )


@router.post("/chats/{chat_id}/mensajes", response_model=RespuestaAsistente)
async def enviar_mensaje(
    chat_id: str, cuerpo: MensajeEntrada, sesion: SesionDb, contexto: Contexto, retell: Retell
) -> RespuestaAsistente:
    """Manda un mensaje y devuelve la respuesta del asistente (RF-AST-002). Puede tardar
    varios segundos: el asistente consulta el inventario antes de responder."""
    return await servicio.conversar(sesion, retell, contexto.negocio_id, chat_id, cuerpo.contenido)


@router.delete("/chats/{chat_id}", status_code=204)
async def terminar_chat(
    chat_id: str, sesion: SesionDb, contexto: Contexto, retell: Retell
) -> Response:
    """Cierra la conversación. Repetirlo no falla."""
    await servicio.terminar(sesion, retell, contexto.negocio_id, chat_id)
    return Response(status_code=204)


_EJEMPLO_LLAMADA = {
    "name": "buscar_producto",
    "args": {"texto": "arroz"},
    "chat": {"chat_id": "Jabr9TXYYJHfvl6Syypi88rdAHYHmcq6"},
}


@router.post(
    "/herramientas/{herramienta}",
    response_model=ResultadoHerramienta,
    openapi_extra={
        "requestBody": {
            "required": True,
            "content": {
                "application/json": {
                    "schema": LlamadaHerramienta.model_json_schema(),
                    "example": _EJEMPLO_LLAMADA,
                }
            },
        }
    },
)
async def herramienta(
    herramienta: Literal["buscar-producto", "stock-bajo", "agotados"],
    request: Request,
    sesion: SesionDb,
    retell: Retell,
    x_retell_signature: Annotated[str | None, Header()] = None,
) -> ResultadoHerramienta:
    """Las llama **Retell**, no la app (RF-AST-003, RF-AST-004). Sin JWT: se autentican con la
    firma `X-Retell-Signature` (HMAC del cuerpo con la API key de Retell). Responden solo con
    datos del negocio dueño del chat."""
    crudo = (await request.body()).decode("utf-8", errors="replace")
    if not retell.firma_valida(crudo, x_retell_signature):
        raise err.NoAutenticado("FIRMA_INVALIDA", "La petición no viene firmada por el asistente.")
    try:
        llamada = LlamadaHerramienta.model_validate_json(crudo or "{}")
    except ValidationError as e:
        raise err.ValidacionInvalida("LLAMADA_INVALIDA", "La llamada no se pudo leer.") from e
    negocio_id = await servicio.negocio_del_chat(sesion, llamada.chat_id())
    if herramienta == "buscar-producto":
        texto = str(llamada.args.get("texto", "")).strip()
        return await servicio.buscar_producto(sesion, negocio_id, texto)
    if herramienta == "stock-bajo":
        return await servicio.stock_bajo(sesion, negocio_id)
    return await servicio.agotados(sesion, negocio_id)
