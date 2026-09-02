"""RF-INV-005, RN-03, RNF-03: registrar movimiento con bloqueo por fila y stock negativo
bloqueado. También RF-INV-002 (rastro), RF-INV-009 (cantidad por unidad) y RF-CAT-011."""

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
    assert r.status_code == 201, r.text
    return {"Authorization": f"Bearer {r.json()['token_acceso']}"}


async def _producto(cliente: httpx.AsyncClient, auth: dict, **extra: object) -> dict:
    cuerpo = {"nombre": "Cuaderno", "unidad_codigo": "unidad"} | extra
    r = await cliente.post("/api/v1/productos", json=cuerpo, headers=auth)
    assert r.status_code == 201, r.text
    return r.json()


async def _mover(cliente: httpx.AsyncClient, auth: dict, **cuerpo: object) -> httpx.Response:
    return await cliente.post(
        "/api/v1/movimientos",
        json=cuerpo,
        headers=auth | {"Idempotency-Key": str(uuid.uuid4())},
    )


async def _stock(cliente: httpx.AsyncClient, auth: dict, producto_id: str) -> str:
    r = await cliente.get(f"/api/v1/productos/{producto_id}/stock", headers=auth)
    assert r.status_code == 200, r.text
    return r.json()["cantidad"]


async def test_rf_inv_001_una_entrada_suma_y_una_salida_resta(cliente: httpx.AsyncClient) -> None:
    auth = await _sesion(cliente)
    p = await _producto(cliente, auth)
    entrada = await _mover(
        cliente, auth, producto_id=p["id"], tipo="entrada", cantidad="10", motivo="carga_inicial"
    )
    assert entrada.status_code == 201, entrada.text
    assert entrada.json()["stock_resultante"] == "10.000"
    assert entrada.json()["direccion"] == 1

    salida = await _mover(
        cliente, auth, producto_id=p["id"], tipo="salida", cantidad="3", motivo="venta"
    )
    assert salida.status_code == 201, salida.text
    assert salida.json()["stock_resultante"] == "7.000"
    assert salida.json()["direccion"] == -1
    assert await _stock(cliente, auth, p["id"]) == "7.000"


async def test_rf_inv_005_salida_mayor_que_el_stock_se_rechaza_con_409_y_el_stock_no_cambia(
    cliente: httpx.AsyncClient,
) -> None:
    auth = await _sesion(cliente)
    p = await _producto(cliente, auth)
    await _mover(
        cliente, auth, producto_id=p["id"], tipo="entrada", cantidad="2", motivo="carga_inicial"
    )
    rechazo = await _mover(
        cliente, auth, producto_id=p["id"], tipo="salida", cantidad="5", motivo="venta"
    )
    assert rechazo.status_code == 409, rechazo.text
    error = rechazo.json()["error"]
    assert error["code"] == "STOCK_INSUFICIENTE"
    assert error["details"]["producto_id"] == p["id"]
    assert error["details"]["solicitado"] == "5.000"
    assert error["details"]["disponible"] == "2.000"
    assert error["details"]["puede_forzar"] is True
    assert "2" in error["message"] and "Cuaderno" in error["message"]
    assert await _stock(cliente, auth, p["id"]) == "2.000"


async def test_rn_03_la_merma_tambien_respeta_el_bloqueo(cliente: httpx.AsyncClient) -> None:
    auth = await _sesion(cliente)
    p = await _producto(cliente, auth)
    rechazo = await _mover(
        cliente,
        auth,
        producto_id=p["id"],
        tipo="merma",
        cantidad="1",
        motivo="rotura",
        nota="Se cayó",
    )
    assert rechazo.status_code == 409
    assert rechazo.json()["error"]["details"]["disponible"] == "0.000"


async def test_rn_03_una_salida_que_deja_el_stock_exactamente_en_cero_se_acepta(
    cliente: httpx.AsyncClient,
) -> None:
    auth = await _sesion(cliente)
    p = await _producto(cliente, auth)
    await _mover(
        cliente, auth, producto_id=p["id"], tipo="entrada", cantidad="2", motivo="carga_inicial"
    )
    ok = await _mover(
        cliente, auth, producto_id=p["id"], tipo="salida", cantidad="2", motivo="venta"
    )
    assert ok.status_code == 201
    assert ok.json()["stock_resultante"] == "0.000"


async def test_rf_inv_009_dos_y_medio_en_unidad_discreta_responde_422(
    cliente: httpx.AsyncClient,
) -> None:
    auth = await _sesion(cliente)
    p = await _producto(cliente, auth)
    r = await _mover(
        cliente, auth, producto_id=p["id"], tipo="entrada", cantidad="2.5", motivo="carga_inicial"
    )
    assert r.status_code == 422
    assert r.json()["error"]["code"] == "CANTIDAD_INVALIDA_PARA_UNIDAD"


async def test_rf_inv_009_dos_y_medio_en_kilos_se_acepta(cliente: httpx.AsyncClient) -> None:
    auth = await _sesion(cliente)
    p = await _producto(cliente, auth, unidad_codigo="kg")
    r = await _mover(
        cliente, auth, producto_id=p["id"], tipo="entrada", cantidad="2.5", motivo="carga_inicial"
    )
    assert r.status_code == 201
    assert r.json()["stock_resultante"] == "2.500"


async def test_rn_07_cantidad_cero_o_negativa_responde_422(cliente: httpx.AsyncClient) -> None:
    auth = await _sesion(cliente)
    p = await _producto(cliente, auth)
    for cantidad in ["0", "-1"]:
        r = await _mover(
            cliente,
            auth,
            producto_id=p["id"],
            tipo="entrada",
            cantidad=cantidad,
            motivo="carga_inicial",
        )
        assert r.status_code == 422, cantidad


async def test_e01_la_cantidad_debe_ser_cadena_no_numero_json(cliente: httpx.AsyncClient) -> None:
    auth = await _sesion(cliente)
    p = await _producto(cliente, auth)
    r = await _mover(
        cliente, auth, producto_id=p["id"], tipo="entrada", cantidad=5, motivo="carga_inicial"
    )
    assert r.status_code == 422


async def test_rf_inv_010_motivo_ajeno_al_tipo_responde_422(cliente: httpx.AsyncClient) -> None:
    auth = await _sesion(cliente)
    p = await _producto(cliente, auth)
    r = await _mover(
        cliente, auth, producto_id=p["id"], tipo="salida", cantidad="1", motivo="rotura"
    )
    assert r.status_code == 422
    assert r.json()["error"]["code"] == "MOTIVO_INVALIDO"


async def test_rf_inv_010_recepcion_compra_la_pone_el_sistema_no_el_cliente(
    cliente: httpx.AsyncClient,
) -> None:
    auth = await _sesion(cliente)
    p = await _producto(cliente, auth)
    r = await _mover(
        cliente, auth, producto_id=p["id"], tipo="entrada", cantidad="1", motivo="recepcion_compra"
    )
    assert r.status_code == 422
    assert r.json()["error"]["code"] == "MOTIVO_RESERVADO"


async def test_rf_inv_008_el_contramovimiento_no_se_registra_a_mano(
    cliente: httpx.AsyncClient,
) -> None:
    auth = await _sesion(cliente)
    p = await _producto(cliente, auth)
    r = await _mover(
        cliente,
        auth,
        producto_id=p["id"],
        tipo="contramovimiento",
        cantidad="1",
        motivo="anulacion",
        nota="x",
    )
    assert r.status_code == 422
    assert r.json()["error"]["code"] == "TIPO_NO_PERMITIDO"


async def test_rn_15_el_ajuste_directo_exige_direccion(cliente: httpx.AsyncClient) -> None:
    auth = await _sesion(cliente)
    p = await _producto(cliente, auth)
    sin = await _mover(
        cliente,
        auth,
        producto_id=p["id"],
        tipo="ajuste",
        cantidad="1",
        motivo="correccion_carga",
        nota="x",
    )
    assert sin.status_code == 422
    con = await _mover(
        cliente,
        auth,
        producto_id=p["id"],
        tipo="ajuste",
        cantidad="1",
        motivo="correccion_carga",
        nota="x",
        direccion=1,
    )
    assert con.status_code == 201
    assert con.json()["stock_resultante"] == "1.000"


async def test_rf_cat_011_un_producto_archivado_no_admite_movimientos(
    cliente: httpx.AsyncClient,
) -> None:
    auth = await _sesion(cliente)
    p = await _producto(cliente, auth)
    await cliente.post(f"/api/v1/productos/{p['id']}/archivar", headers=auth)
    r = await _mover(
        cliente, auth, producto_id=p["id"], tipo="entrada", cantidad="1", motivo="carga_inicial"
    )
    assert r.status_code == 409
    assert r.json()["error"]["code"] == "PRODUCTO_ARCHIVADO"


async def test_rf_aut_007_mover_un_producto_ajeno_responde_404(cliente: httpx.AsyncClient) -> None:
    a, b = await _sesion(cliente), await _sesion(cliente)
    p = await _producto(cliente, a)
    r = await _mover(
        cliente, b, producto_id=p["id"], tipo="entrada", cantidad="1", motivo="carga_inicial"
    )
    assert r.status_code == 404
    assert r.json()["error"]["code"] == "PRODUCTO_NO_ENCONTRADO"


async def test_rf_inv_002_el_movimiento_guarda_autor_origen_y_momento(
    cliente: httpx.AsyncClient, sesion: AsyncSession
) -> None:
    auth = await _sesion(cliente)
    p = await _producto(cliente, auth)
    r = await _mover(
        cliente,
        auth,
        producto_id=p["id"],
        tipo="entrada",
        cantidad="1",
        motivo="carga_inicial",
        nota="Primera carga",
    )
    datos = r.json()
    assert datos["autor"]["tipo"] == "usuario"
    assert datos["origen"] == "app"
    assert datos["ocurrido_en"].endswith("Z") or "+00:00" in datos["ocurrido_en"]
    assert datos["nota"] == "Primera carga"
    assert datos["forzado"] is False and datos["anulado_en"] is None
    fila = (
        await sesion.execute(
            sa.text("select autor_tipo, origen from movimientos where id = :id"),
            {"id": datos["id"]},
        )
    ).one()
    assert (fila.autor_tipo, fila.origen) == ("usuario", "app")


async def test_rf_inv_002_con_api_key_el_origen_es_api_y_el_autor_servicio(
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
    assert r.status_code == 201, r.text
    assert r.json()["origen"] == "api" and r.json()["autor"]["tipo"] == "servicio"


async def test_rf_cat_008_la_ficha_y_el_escaneo_traen_el_stock_actual(
    cliente: httpx.AsyncClient,
) -> None:
    auth = await _sesion(cliente)
    p = await _producto(cliente, auth, codigos_barras=["7701"])
    await _mover(
        cliente, auth, producto_id=p["id"], tipo="entrada", cantidad="4", motivo="carga_inicial"
    )
    ficha = await cliente.get(f"/api/v1/productos/{p['id']}", headers=auth)
    escaneo = await cliente.get("/api/v1/productos/por-codigo/7701", headers=auth)
    assert ficha.json()["stock_actual"] == "4.000"
    assert escaneo.json()["stock_actual"] == "4.000"


async def test_rf_inv_003_el_stock_de_un_producto_nuevo_es_cero(cliente: httpx.AsyncClient) -> None:
    auth = await _sesion(cliente)
    p = await _producto(cliente, auth)
    assert p["stock_actual"] == "0.000"
    assert await _stock(cliente, auth, p["id"]) == "0.000"
