"""URLs de lectura firmadas con HMAC y caducidad (RNF-11, RNF-16). La clave de almacenamiento
nunca viaja: el token lleva un identificador opaco y el servidor lo resuelve."""

import base64
import hashlib
import hmac
import json
import time
from datetime import timedelta

from app.config import obtener_ajustes


def _firma(carga: bytes) -> str:
    secreto = obtener_ajustes().imagenes_secreto.encode()
    return hmac.new(secreto, carga, hashlib.sha256).hexdigest()


def firmar_lectura(clave: str, ttl: timedelta) -> str:
    """Devuelve la ruta relativa de la API con el token. El identificador público es un hash
    de la clave, no la clave."""
    identificador = hashlib.sha256(clave.encode()).hexdigest()[:32]
    caduca = int(time.time() + ttl.total_seconds())
    carga = json.dumps({"i": identificador, "e": caduca}, separators=(",", ":")).encode()
    token = base64.urlsafe_b64encode(carga).decode().rstrip("=") + "." + _firma(carga)
    return f"/api/v1/imagenes/{identificador}?t={token}"


def verificar_token(token: str, identificador: str) -> bool:
    """Verifica firma, identificador y caducidad. Sin excepciones: True o False."""
    try:
        carga_b64, firma = token.split(".", 1)
        carga = base64.urlsafe_b64decode(carga_b64 + "=" * (-len(carga_b64) % 4))
        datos = json.loads(carga)
    except (ValueError, TypeError):
        return False
    if not hmac.compare_digest(_firma(carga), firma):
        return False
    return datos.get("i") == identificador and int(datos.get("e", 0)) > time.time()
