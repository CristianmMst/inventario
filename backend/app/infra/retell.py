"""Cliente de la API de chat de Retell (RF-AST-001, RF-AST-002) y verificación de la firma con
que Retell llama a nuestras herramientas (RF-AST-004).

La API key solo vive en el servidor: la app habla con nuestra API y nunca ve la clave.
"""

import hashlib
import hmac
import re
import time
from typing import Any

import httpx
import structlog

from app.config import obtener_ajustes
from app.dominio import errores as err

log = structlog.get_logger()

# `x-retell-signature: v=<epoch ms>,d=<hex HMAC-SHA256(cuerpo + epoch ms)>`, como en el SDK.
_FIRMA = re.compile(r"v=(\d+),d=([0-9a-f]{64})")
VENTANA_FIRMA_MS = 5 * 60 * 1000


def _no_disponible() -> err.ServicioExterno:
    return err.ServicioExterno(
        "ASISTENTE_NO_DISPONIBLE", "El asistente no responde ahora. Intenta de nuevo en un rato."
    )


class ClienteRetell:
    def __init__(self, api_key: str, agent_id: str, url_base: str) -> None:
        self.api_key = api_key
        self.agent_id = agent_id
        self._url_base = url_base.rstrip("/")

    def _exigir_configuracion(self) -> None:
        if not self.api_key or not self.agent_id:
            raise err.ServicioExterno(
                "ASISTENTE_NO_CONFIGURADO", "El asistente todavía no está activado en el servidor."
            )

    async def _post(self, ruta: str, cuerpo: dict[str, Any]) -> dict[str, Any]:
        self._exigir_configuracion()
        try:
            async with httpx.AsyncClient(timeout=httpx.Timeout(60.0, connect=10.0)) as http:
                r = await http.post(
                    f"{self._url_base}{ruta}",
                    json=cuerpo,
                    headers={"Authorization": f"Bearer {self.api_key}"},
                )
        except httpx.HTTPError as e:
            log.warning("retell_inalcanzable", ruta=ruta, error=str(e))
            raise _no_disponible() from e
        if r.status_code >= 400:
            log.warning("retell_error", ruta=ruta, status=r.status_code, cuerpo=r.text[:500])
            raise _no_disponible()
        return r.json() if r.content else {}

    async def crear_chat(self, metadata: dict[str, str]) -> str:
        datos = await self._post("/create-chat", {"agent_id": self.agent_id, "metadata": metadata})
        return str(datos["chat_id"])

    async def completar(self, chat_id: str, contenido: str) -> list[str]:
        """Devuelve solo lo que el agente le dice al usuario; las llamadas a herramientas y los
        cambios de nodo quedan del lado de Retell."""
        datos = await self._post(
            "/create-chat-completion", {"chat_id": chat_id, "content": contenido}
        )
        return [
            str(m["content"])
            for m in datos.get("messages", [])
            if m.get("role") == "agent" and m.get("content")
        ]

    async def terminar_chat(self, chat_id: str) -> None:
        self._exigir_configuracion()
        try:
            async with httpx.AsyncClient(timeout=10.0) as http:
                await http.patch(
                    f"{self._url_base}/end-chat/{chat_id}",
                    headers={"Authorization": f"Bearer {self.api_key}"},
                )
        except httpx.HTTPError as e:
            # Terminar es cortesía: Retell cierra solo los chats inactivos.
            log.warning("retell_end_chat_fallo", chat_id=chat_id, error=str(e))

    def firma_valida(self, cuerpo: str, firma: str | None, ahora_ms: int | None = None) -> bool:
        """Sin excepciones: True solo si la firma es de nuestra clave y es reciente."""
        if not self.api_key or not firma:
            return False
        coincide = _FIRMA.fullmatch(firma.strip())
        if not coincide:
            return False
        sello, digest = int(coincide.group(1)), coincide.group(2)
        ahora = ahora_ms if ahora_ms is not None else int(time.time() * 1000)
        if abs(ahora - sello) > VENTANA_FIRMA_MS:
            return False
        esperado = hmac.new(
            self.api_key.encode(), (cuerpo + str(sello)).encode(), hashlib.sha256
        ).hexdigest()
        return hmac.compare_digest(esperado, digest)


def firmar(cuerpo: str, api_key: str, ahora_ms: int | None = None) -> str:
    """La misma firma que pone Retell. Para pruebas y para depurar a mano."""
    sello = ahora_ms if ahora_ms is not None else int(time.time() * 1000)
    digest = hmac.new(api_key.encode(), (cuerpo + str(sello)).encode(), hashlib.sha256)
    return f"v={sello},d={digest.hexdigest()}"


def cliente_retell() -> ClienteRetell:
    """Dependencia de FastAPI; las pruebas la reemplazan por un falso."""
    a = obtener_ajustes()
    return ClienteRetell(a.retell_api_key, a.retell_agent_id, a.retell_url_base)
