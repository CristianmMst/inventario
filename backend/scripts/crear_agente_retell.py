"""Prepara en Retell el agente de chat del asistente de inventario (RF-AST-003).

    uv run python scripts/crear_agente_retell.py --actualizar  # adapta el de RETELL_AGENT_ID
    uv run python scripts/crear_agente_retell.py               # crea flujo + agente nuevos

`--actualizar` conserva el flujo que se armó en el dashboard (nodos, prompt, PQR) y solo
cambia las herramientas: quita `query_inventory` y pone las tres firmadas del asistente.

Lee `RETELL_API_KEY`, `RETELL_AGENT_ID` y `URL_PUBLICA_API` de `.env`. Imprime el `agent_id`,
que hay que poner en `RETELL_AGENT_ID` del servidor.

El prompt sale del agente de voz «Asistente de Inventarios» que ya existía en la cuenta; aquí
cambia la herramienta: en vez de `/integracion/stock-bajo` (pública y de todos los negocios) usa
las herramientas firmadas del asistente, que solo ven el negocio dueño del chat (RN-19). El
agente de voz no se toca.
"""

import argparse
import sys
from typing import Any

import httpx

from app.config import obtener_ajustes

MODELO = {"type": "cascading", "model": "gpt-5.6-terra"}

PROMPT_GLOBAL = """## Rol
Eres el asistente de inventario de un pequeño negocio. Ayudas al dueño a monitorear y \
gestionar sus productos usando exclusivamente los datos que devuelven tus herramientas. No \
inventes cifras ni productos; si un dato no está disponible (por ejemplo, historial de \
rotación, categoría o ubicación), dilo.

## Herramientas
- `buscar_producto(texto)`: busca por nombre, SKU o código de barras y devuelve stock actual, \
stock mínimo y estado.
- `stock_bajo()`: productos con stock igual o por debajo del mínimo, el más urgente primero.
- `agotados()`: productos sin existencias.
Todas devuelven solo datos del negocio de quien conversa. Si `hay_mas` es verdadero, aclara \
que la lista está recortada.

## Clasificación de stock
- Óptimo: por encima del mínimo.
- Bajo mínimo (punto de reorden): igual o por debajo del mínimo; hay que reponer pronto.
- Agotado: sin existencias.

## Formato de respuesta
- Es un chat en una app móvil: respuestas cortas.
- Empieza con un resumen de una línea (ej. "Tienes 3 productos agotados y 5 bajo el mínimo").
- Luego una lista con viñetas: **Nombre** (SKU) — cantidad actual / mínimo, unidad.
- Sugerencia de compra: como mínimo, reponer hasta el stock mínimo.
- Nada de tablas ni párrafos largos.

## Tono
Profesional, directo y en español. Sin saludos excesivos; ve directo a los datos."""

INSTRUCCION_NODO = """Atiende las consultas de inventario del usuario.
- "¿Qué me falta?", "¿qué está por agotarse?", "¿qué debo comprar?": usa `stock_bajo` y \
`agotados`.
- Preguntas por un producto concreto: usa `buscar_producto` con el nombre o SKU que dijo el \
usuario. Si no aparece, dilo y sugiere revisar el nombre.
- Preguntas fuera del inventario (ventas, precios de la competencia, etc.): explica con \
amabilidad que solo puedes consultar el stock del negocio."""


def _herramientas(url_api: str) -> list[dict[str, Any]]:
    base = f"{url_api.rstrip('/')}/api/v1/asistente/herramientas"
    sin_parametros = {"type": "object", "properties": {}}
    return [
        {
            "tool_id": "buscar_producto",
            "type": "custom",
            "name": "buscar_producto",
            "description": "Busca productos del negocio por nombre, SKU o código de barras. "
            "Devuelve stock_actual, stock_minimo, unidad y estado_stock "
            "(agotado, bajo_minimo u optimo).",
            "url": f"{base}/buscar-producto",
            "method": "POST",
            "parameters": {
                "type": "object",
                "properties": {
                    "texto": {
                        "type": "string",
                        "description": "Nombre, parte del nombre, SKU o código de barras.",
                    }
                },
                "required": ["texto"],
            },
            "timeout_ms": 20000,
        },
        {
            "tool_id": "stock_bajo",
            "type": "custom",
            "name": "stock_bajo",
            "description": "Productos del negocio con stock igual o por debajo de su mínimo, "
            "el más urgente primero.",
            "url": f"{base}/stock-bajo",
            "method": "POST",
            "parameters": sin_parametros,
            "timeout_ms": 20000,
        },
        {
            "tool_id": "agotados",
            "type": "custom",
            "name": "agotados",
            "description": "Productos del negocio sin existencias (stock 0 o menos).",
            "url": f"{base}/agotados",
            "method": "POST",
            "parameters": sin_parametros,
            "timeout_ms": 20000,
        },
    ]


def _flujo(url_api: str) -> dict[str, Any]:
    return {
        "start_speaker": "user",
        "model_choice": MODELO,
        "global_prompt": PROMPT_GLOBAL,
        "tools": _herramientas(url_api),
        "start_node_id": "asistente_inventario",
        "nodes": [
            {
                "id": "asistente_inventario",
                "name": "Asistente de inventario",
                "type": "subagent",
                "instruction": {"type": "prompt", "text": INSTRUCCION_NODO},
                "tool_ids": ["buscar_producto", "stock_bajo", "agotados"],
            }
        ],
    }


def _agente(flujo_id: str) -> dict[str, Any]:
    return {
        "agent_name": "Asistente de Inventarios (chat)",
        "response_engine": {"type": "conversation-flow", "conversation_flow_id": flujo_id},
        "language": "es-419",
        "timezone": "America/Bogota",
        "data_storage_setting": "everything",
    }


HERRAMIENTAS = ["buscar_producto", "stock_bajo", "agotados"]
MENCION = "tus herramientas (`buscar_producto`, `stock_bajo` y `agotados`)"
ALCANCE = (
    "\n\n## Alcance de los datos\nTus herramientas solo devuelven productos del negocio de "
    "quien conversa. No hace falta preguntar de qué negocio se trata."
)


def _adaptar(flujo: dict[str, Any], url_api: str) -> dict[str, Any]:
    """Cambia solo las herramientas de un flujo existente. `query_inventory` leía
    `/integracion/stock-bajo`, que trae todos los negocios; las nuevas ven solo el del chat.
    Es repetible: las herramientas `custom` se reemplazan siempre por las de esta versión."""
    viejas = {t["tool_id"] for t in flujo.get("tools") or [] if t.get("type") == "custom"}
    otras = [t for t in flujo.get("tools") or [] if t["tool_id"] not in viejas]
    nodos = []
    for nodo in flujo["nodes"]:
        nodo = dict(nodo)
        if set(nodo.get("tool_ids") or []) & viejas:
            ids = [i for i in nodo["tool_ids"] if i not in viejas]
            nodo["tool_ids"] = ids + HERRAMIENTAS
        instruccion = nodo.get("instruction")
        if instruccion and instruccion.get("type") == "prompt":
            texto = instruccion["text"].replace("`query_inventory`", MENCION)
            nodo["instruction"] = instruccion | {"text": texto}
        nodos.append(nodo)
    prompt = (flujo.get("global_prompt") or "").split(ALCANCE)[0]
    prompt = prompt.replace("devuelve la herramienta `query_inventory`", f"devuelven {MENCION}")
    return {
        "global_prompt": prompt + ALCANCE,
        "tools": otras + _herramientas(url_api),
        "nodes": nodos,
    }


def _llamar(http: httpx.Client, metodo: str, ruta: str, cuerpo: Any = None) -> dict[str, Any]:
    r = http.request(metodo, ruta, json=cuerpo)
    if r.status_code >= 400:
        sys.exit(f"Retell respondió {r.status_code} a {metodo} {ruta}: {r.text}")
    return r.json() if r.content else {}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--actualizar", action="store_true", help="actualiza RETELL_AGENT_ID")
    args = parser.parse_args()

    ajustes = obtener_ajustes()
    if not ajustes.retell_api_key:
        sys.exit("Falta RETELL_API_KEY en .env")
    http = httpx.Client(
        base_url=ajustes.retell_url_base,
        headers={"Authorization": f"Bearer {ajustes.retell_api_key}"},
        timeout=30.0,
    )
    url = ajustes.url_publica_api

    if args.actualizar:
        if not ajustes.retell_agent_id:
            sys.exit("Falta RETELL_AGENT_ID en .env para actualizar")
        agente = _llamar(http, "GET", f"/get-chat-agent/{ajustes.retell_agent_id}")
        flujo_id = agente["response_engine"]["conversation_flow_id"]
        actual = _llamar(http, "GET", f"/get-conversation-flow/{flujo_id}")
        _llamar(http, "PATCH", f"/update-conversation-flow/{flujo_id}", _adaptar(actual, url))
        print(f"Flujo {flujo_id} del agente {ajustes.retell_agent_id} con herramientas firmadas")
        return

    flujo_id = _llamar(http, "POST", "/create-conversation-flow", _flujo(url))[
        "conversation_flow_id"
    ]
    agente_id = _llamar(http, "POST", "/create-chat-agent", _agente(flujo_id))["agent_id"]
    print(f"conversation_flow_id = {flujo_id}")
    print(f"agent_id             = {agente_id}")
    print(f"\nPon en el .env del servidor:\nRETELL_AGENT_ID={agente_id}")


if __name__ == "__main__":
    main()
