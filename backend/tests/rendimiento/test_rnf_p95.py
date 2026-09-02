"""T-097: p95 de RNF-01 a RNF-04 sobre la base sembrada (scripts/semilla.py). Rompe el build.

Corre contra una API levantada con el volumen de referencia, no contra testcontainers:

    RENDIMIENTO_URL=http://localhost:8000 uv run pytest tests/rendimiento -q

Sin `RENDIMIENTO_URL` los tests se saltan: la suite normal no depende de la semilla. La
credencial por defecto es la que crea la semilla; se puede cambiar con RENDIMIENTO_EMAIL y
RENDIMIENTO_PASSWORD. Cada medición hace un calentamiento y luego N peticiones secuenciales;
el p95 se calcula sobre el tiempo de respuesta visto por el cliente HTTP.
"""

from __future__ import annotations

import os
import statistics
import time
import uuid
from collections.abc import Callable
from datetime import date, timedelta

import httpx
import pytest

URL = os.environ.get("RENDIMIENTO_URL")
EMAIL = os.environ.get("RENDIMIENTO_EMAIL", "semilla@ejemplo.com")
PASSWORD = os.environ.get("RENDIMIENTO_PASSWORD", "Semilla-de-referencia-2026")
N = int(os.environ.get("RENDIMIENTO_N", "40"))
CALENTAMIENTO = 3

pytestmark = [
    pytest.mark.rendimiento,
    pytest.mark.skipif(not URL, reason="RENDIMIENTO_URL no definida: estos tests miden contra la base sembrada"),
]


def p95(muestras: list[float]) -> float:
    ordenadas = sorted(muestras)
    indice = max(0, round(0.95 * len(ordenadas)) - 1)
    return ordenadas[indice]


def medir(peticion: Callable[[], httpx.Response], n: int = N) -> tuple[float, float]:
    """Devuelve (p95_ms, mediana_ms) tras calentar; falla si alguna respuesta no es 2xx."""
    for _ in range(CALENTAMIENTO):
        peticion().raise_for_status()
    muestras: list[float] = []
    for _ in range(n):
        inicio = time.perf_counter()
        respuesta = peticion()
        muestras.append((time.perf_counter() - inicio) * 1000)
        respuesta.raise_for_status()
    return p95(muestras), statistics.median(muestras)


@pytest.fixture(scope="module")
def cliente() -> httpx.Client:
    with httpx.Client(base_url=URL or "", timeout=30) as c:
        sesion = c.post("/api/v1/auth/login", json={"email": EMAIL, "password": PASSWORD})
        assert sesion.status_code == 200, f"no se pudo iniciar sesión con la cuenta de la semilla: {sesion.text}"
        c.headers["Authorization"] = f"Bearer {sesion.json()['token_acceso']}"
        yield c


@pytest.fixture(scope="module")
def producto_de_referencia(cliente: httpx.Client) -> dict:
    respuesta = cliente.get("/api/v1/productos/buscar", params={"q": "cuaderno", "limit": 1})
    respuesta.raise_for_status()
    datos = respuesta.json()["datos"]
    assert datos, "la base no parece sembrada: no hay cuadernos"
    return datos[0]


def informar(nombre: str, p95_ms: float, mediana_ms: float, umbral_ms: float) -> None:
    print(f"\n{nombre}: p95 {p95_ms:.0f} ms · mediana {mediana_ms:.0f} ms · umbral {umbral_ms:.0f} ms")


def test_rnf_01_busqueda_por_codigo_de_barras_bajo_300_ms(cliente: httpx.Client, producto_de_referencia: dict) -> None:
    codigos = producto_de_referencia["codigos_barras"]
    assert codigos, "el producto de referencia no tiene código de barras"
    p95_ms, mediana = medir(lambda: cliente.get(f"/api/v1/productos/por-codigo/{codigos[0]}"))
    informar("RNF-01 por código", p95_ms, mediana, 300)
    assert p95_ms < 300


def test_rnf_02_busqueda_por_texto_bajo_500_ms(cliente: httpx.Client) -> None:
    consultas = ["cuad", "lapiz", "resma a4", "marcador negro", "tijeras"]
    contador = {"i": 0}

    def peticion() -> httpx.Response:
        q = consultas[contador["i"] % len(consultas)]
        contador["i"] += 1
        return cliente.get("/api/v1/productos/buscar", params={"q": q})

    p95_ms, mediana = medir(peticion)
    informar("RNF-02 por texto", p95_ms, mediana, 500)
    assert p95_ms < 500


def test_rnf_03_registro_de_movimiento_bajo_400_ms(cliente: httpx.Client, producto_de_referencia: dict) -> None:
    producto_id = producto_de_referencia["id"]

    def peticion() -> httpx.Response:
        return cliente.post(
            "/api/v1/movimientos",
            headers={"Idempotency-Key": str(uuid.uuid4())},
            json={"producto_id": producto_id, "tipo": "entrada", "cantidad": "1", "motivo": "carga_inicial", "nota": "medición RNF-03"},
        )

    p95_ms, mediana = medir(peticion)
    informar("RNF-03 movimiento", p95_ms, mediana, 400)
    assert p95_ms < 400


@pytest.mark.parametrize(
    "ruta, params",
    [
        ("/api/v1/reportes/valorizacion", {}),
        ("/api/v1/reportes/bajo-minimo", {}),
        ("/api/v1/reportes/compras", {"desde": (date.today() - timedelta(days=365)).isoformat(), "hasta": date.today().isoformat()}),
    ],
    ids=["valorizacion", "bajo-minimo", "compras"],
)
def test_rnf_04_reportes_bajo_2_s(cliente: httpx.Client, ruta: str, params: dict) -> None:
    p95_ms, mediana = medir(lambda: cliente.get(ruta, params=params), n=max(10, N // 2))
    informar(f"RNF-04 {ruta.rsplit('/', 1)[-1]}", p95_ms, mediana, 2000)
    assert p95_ms < 2000
