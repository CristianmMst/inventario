"""RF-INT-001, RF-INT-004, RN-21, RN-22, RF-CAT-012: eventos persistidos en la misma
transacción del hecho, antirrebote de bajo mínimo y consulta paginada por secuencia."""

import uuid

import httpx
import sqlalchemy as sa
from sqlalchemy.ext.asyncio import AsyncSession

from tests import fabricas


async def _sesion(cliente: httpx.AsyncClient) -> dict:
    cuerpo = {
        "email": fabricas.correo_unico(),
        "password": fabricas.CONTRASENA_VALIDA,
        "nombre": "Marta",
        "negocio": {"nombre": "P", "moneda_base": "COP", "zona_horaria": "UTC"},
    }
    r = await cliente.post("/api/v1/auth/registro", json=cuerpo)
    return {"Authorization": f"Bearer {r.json()['token_acceso']}"}


def _clave() -> dict[str, str]:
    return {"Idempotency-Key": str(uuid.uuid4())}


async def _producto(cliente: httpx.AsyncClient, auth: dict, **extra: object) -> dict:
    cuerpo = {"nombre": "Cuaderno", "unidad_codigo": "unidad"} | extra
    r = await cliente.post("/api/v1/productos", json=cuerpo, headers=auth)
    assert r.status_code == 201, r.text
    return r.json()


async def _mover(cliente: httpx.AsyncClient, auth: dict, **cuerpo: object) -> httpx.Response:
    return await cliente.post("/api/v1/movimientos", json=cuerpo, headers=auth | _clave())


async def _eventos(cliente: httpx.AsyncClient, auth: dict, **params: object) -> list[dict]:
    r = await cliente.get("/api/v1/eventos", params=params, headers=auth)
    assert r.status_code == 200, r.text
    return r.json()["datos"]


async def test_rf_int_001_registrar_un_movimiento_deja_su_evento_con_el_payload_de_la_spec(
    cliente: httpx.AsyncClient, sesion: AsyncSession
) -> None:
    auth = await _sesion(cliente)
    p = await _producto(cliente, auth)
    m = (
        await _mover(
            cliente,
            auth,
            producto_id=p["id"],
            tipo="entrada",
            cantidad="10",
            motivo="carga_inicial",
        )
    ).json()
    eventos = await _eventos(cliente, auth, tipo="movimiento.registrado")
    assert len(eventos) == 1
    e = eventos[0]
    assert set(e) >= {
        "id",
        "secuencia",
        "tipo",
        "version",
        "business_id",
        "ocurrido_en",
        "autor",
        "payload",
    }
    assert e["version"] == 1 and e["autor"]["tipo"] == "usuario" and e["autor"]["nombre"] == "Marta"
    assert e["payload"] == {
        "movimiento_id": m["id"],
        "producto_id": p["id"],
        "tipo": "entrada",
        "cantidad": "10.000",
        "motivo": "carga_inicial",
        "nota": None,
        "forzado": False,
        "stock_resultante": "10.000",
        "origen": "app",
        "recepcion_id": None,
    }
    fila = (await sesion.execute(sa.text("select count(*) from eventos"))).scalar_one()
    assert fila == len(await _eventos(cliente, auth))


async def test_rn_21_si_el_movimiento_falla_no_queda_evento_huerfano(
    cliente: httpx.AsyncClient, sesion: AsyncSession
) -> None:
    auth = await _sesion(cliente)
    p = await _producto(cliente, auth)
    antes = (await sesion.execute(sa.text("select count(*) from eventos"))).scalar_one()
    rechazo = await _mover(
        cliente, auth, producto_id=p["id"], tipo="salida", cantidad="5", motivo="venta"
    )
    assert rechazo.status_code == 409
    despues = (await sesion.execute(sa.text("select count(*) from eventos"))).scalar_one()
    assert despues == antes
    movimientos = (await sesion.execute(sa.text("select count(*) from movimientos"))).scalar_one()
    registrados = (
        await sesion.execute(
            sa.text("select count(*) from eventos where tipo = 'movimiento.registrado'")
        )
    ).scalar_one()
    assert movimientos == registrados == 0


async def test_rn_21_no_hay_hecho_sin_evento(
    cliente: httpx.AsyncClient, sesion: AsyncSession
) -> None:
    auth = await _sesion(cliente)
    p = await _producto(cliente, auth)
    for _ in range(3):
        await _mover(
            cliente, auth, producto_id=p["id"], tipo="entrada", cantidad="1", motivo="carga_inicial"
        )
    movimientos = (await sesion.execute(sa.text("select count(*) from movimientos"))).scalar_one()
    registrados = (
        await sesion.execute(
            sa.text("select count(*) from eventos where tipo = 'movimiento.registrado'")
        )
    ).scalar_one()
    assert movimientos == registrados == 3


async def test_rn_22_bajar_del_minimo_emite_una_vez_y_no_se_repite_hasta_recuperarse(
    cliente: httpx.AsyncClient,
) -> None:
    auth = await _sesion(cliente)
    p = await _producto(cliente, auth, stock_minimo="10")
    await _mover(
        cliente, auth, producto_id=p["id"], tipo="entrada", cantidad="12", motivo="carga_inicial"
    )
    assert await _eventos(cliente, auth, tipo="stock.bajo_minimo") == []

    await _mover(
        cliente, auth, producto_id=p["id"], tipo="salida", cantidad="4", motivo="venta"
    )  # 8
    bajos = await _eventos(cliente, auth, tipo="stock.bajo_minimo")
    assert len(bajos) == 1
    assert bajos[0]["payload"]["stock_actual"] == "8.000"
    assert bajos[0]["payload"]["stock_minimo"] == "10.000"
    assert bajos[0]["payload"]["deficit"] == "2.000"
    assert bajos[0]["payload"]["nombre"] == "Cuaderno" and bajos[0]["payload"]["unidad"] == "unidad"

    await _mover(
        cliente, auth, producto_id=p["id"], tipo="salida", cantidad="1", motivo="venta"
    )  # 7
    await _mover(
        cliente, auth, producto_id=p["id"], tipo="salida", cantidad="1", motivo="venta"
    )  # 6
    assert len(await _eventos(cliente, auth, tipo="stock.bajo_minimo")) == 1

    await _mover(
        cliente, auth, producto_id=p["id"], tipo="entrada", cantidad="10", motivo="carga_inicial"
    )  # 16
    repuestos = await _eventos(cliente, auth, tipo="stock.repuesto")
    assert len(repuestos) == 1 and repuestos[0]["payload"]["stock_actual"] == "16.000"

    await _mover(
        cliente, auth, producto_id=p["id"], tipo="salida", cantidad="8", motivo="venta"
    )  # 8
    assert len(await _eventos(cliente, auth, tipo="stock.bajo_minimo")) == 2


async def test_rf_cat_012_llegar_a_cero_emite_agotado(cliente: httpx.AsyncClient) -> None:
    auth = await _sesion(cliente)
    p = await _producto(cliente, auth)
    await _mover(
        cliente, auth, producto_id=p["id"], tipo="entrada", cantidad="2", motivo="carga_inicial"
    )
    await _mover(cliente, auth, producto_id=p["id"], tipo="salida", cantidad="2", motivo="venta")
    agotados = await _eventos(cliente, auth, tipo="stock.agotado")
    assert len(agotados) == 1
    assert agotados[0]["payload"] == {
        "producto_id": p["id"],
        "nombre": "Cuaderno",
        "stock_actual": "0.000",
        "unidad": "unidad",
    }
    # Sin mínimo definido no hay bajo_minimo, pero sí agotado (RF-REP-001 vs RF-REP-007).
    assert await _eventos(cliente, auth, tipo="stock.bajo_minimo") == []


async def test_rn_22_el_evento_de_stock_se_escribe_en_la_misma_transaccion_que_el_movimiento(
    cliente: httpx.AsyncClient, sesion: AsyncSession
) -> None:
    auth = await _sesion(cliente)
    p = await _producto(cliente, auth, stock_minimo="5")
    await _mover(
        cliente, auth, producto_id=p["id"], tipo="entrada", cantidad="3", motivo="carga_inicial"
    )
    filas = (await sesion.execute(sa.text("select tipo from eventos order by secuencia"))).scalars()
    assert list(filas)[-2:] == ["movimiento.registrado", "stock.bajo_minimo"]


async def test_rf_int_004_un_consumidor_se_pone_al_dia_desde_una_secuencia_sin_saltos_ni_repeticiones(
    cliente: httpx.AsyncClient,
) -> None:
    auth = await _sesion(cliente)
    p = await _producto(cliente, auth)
    for _ in range(7):
        await _mover(
            cliente, auth, producto_id=p["id"], tipo="entrada", cantidad="1", motivo="carga_inicial"
        )
    todos = await _eventos(cliente, auth, limit=100)
    secuencias = [e["secuencia"] for e in todos]
    assert secuencias == sorted(secuencias) and len(set(secuencias)) == len(secuencias)

    visto = []
    desde = 0
    while True:
        r = await cliente.get(
            "/api/v1/eventos", params={"desde_secuencia": desde, "limit": 3}, headers=auth
        )
        pagina = r.json()
        visto += [e["secuencia"] for e in pagina["datos"]]
        if not pagina["tiene_mas"]:
            break
        desde = pagina["datos"][-1]["secuencia"]
    assert visto == secuencias


async def test_rf_int_004_filtros_por_tipo_y_rango_de_fechas(cliente: httpx.AsyncClient) -> None:
    auth = await _sesion(cliente)
    p = await _producto(cliente, auth)
    await _mover(
        cliente, auth, producto_id=p["id"], tipo="entrada", cantidad="1", motivo="carga_inicial"
    )
    creados = await _eventos(cliente, auth, tipo="producto.creado")
    assert len(creados) == 1 and creados[0]["payload"]["producto_id"] == p["id"]
    nada = await _eventos(cliente, auth, desde="2000-01-01T00:00:00Z", hasta="2001-01-01T00:00:00Z")
    assert nada == []
    algo = await _eventos(cliente, auth, desde="2026-01-01T00:00:00Z")
    assert len(algo) >= 2


async def test_rf_aut_007_los_eventos_no_cruzan_negocios(cliente: httpx.AsyncClient) -> None:
    a, b = await _sesion(cliente), await _sesion(cliente)
    p = await _producto(cliente, a)
    await _mover(
        cliente, a, producto_id=p["id"], tipo="entrada", cantidad="1", motivo="carga_inicial"
    )
    assert await _eventos(cliente, b) == []


async def test_rf_int_008_los_eventos_se_leen_con_api_key_y_el_autor_de_servicio_lleva_su_nombre(
    cliente: httpx.AsyncClient,
) -> None:
    auth = await _sesion(cliente)
    clave = (await cliente.post("/api/v1/api-keys", json={"nombre": "n8n"}, headers=auth)).json()[
        "clave"
    ]
    p = await _producto(cliente, auth)
    r = await _mover(
        cliente,
        {"X-API-Key": clave},
        producto_id=p["id"],
        tipo="entrada",
        cantidad="1",
        motivo="carga_inicial",
    )
    assert r.status_code == 201
    eventos = await _eventos(cliente, {"X-API-Key": clave}, tipo="movimiento.registrado")
    assert eventos[0]["autor"] == {
        "tipo": "servicio",
        "id": eventos[0]["autor"]["id"],
        "nombre": "n8n",
    }
