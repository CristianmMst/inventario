"""RF-COM-004, RF-COM-005, RN-11: recepción en borrador, con o sin orden de compra."""

import uuid

import httpx

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


async def _proveedor(cliente: httpx.AsyncClient, auth: dict, nombre: str = "Papeles SAS") -> dict:
    return (await cliente.post("/api/v1/proveedores", json={"nombre": nombre}, headers=auth)).json()


async def _producto(cliente: httpx.AsyncClient, auth: dict, **extra: object) -> dict:
    cuerpo = {"nombre": "Cuaderno", "unidad_codigo": "unidad"} | extra
    return (await cliente.post("/api/v1/productos", json=cuerpo, headers=auth)).json()


def _linea(
    producto_id: str, cantidad: str = "10", costo: str = "2500", moneda: str = "COP"
) -> dict:
    return {
        "producto_id": producto_id,
        "cantidad": cantidad,
        "costo_unitario": {"monto": costo, "moneda": moneda},
    }


async def _recepcion(cliente: httpx.AsyncClient, auth: dict, **cuerpo: object) -> httpx.Response:
    return await cliente.post("/api/v1/recepciones", json=cuerpo, headers=auth)


async def _orden_emitida(
    cliente: httpx.AsyncClient, auth: dict, prov: dict, lineas: list[dict]
) -> dict:
    o = (
        await cliente.post(
            "/api/v1/ordenes-compra",
            json={"proveedor_id": prov["id"], "lineas": lineas},
            headers=auth,
        )
    ).json()
    r = await cliente.post(f"/api/v1/ordenes-compra/{o['id']}/emitir", headers=auth)
    assert r.status_code == 200, r.text
    return r.json()


async def test_rf_com_004_recepcion_directa_sin_orden(cliente: httpx.AsyncClient) -> None:
    auth = await _sesion(cliente)
    prov = await _proveedor(cliente, auth)
    p = await _producto(cliente, auth)
    r = await _recepcion(
        cliente, auth, proveedor_id=prov["id"], lineas=[_linea(p["id"])], notas="Trajo el camión"
    )
    assert r.status_code == 201, r.text
    rc = r.json()
    assert rc["estado"] == "borrador"
    assert rc["numero"] == "RC-000001"
    assert rc["orden"] is None
    assert rc["proveedor"]["id"] == prov["id"]
    assert rc["moneda"] == "COP" and rc["tasa_cambio"] == "1.00000000"
    assert rc["fecha"]
    linea = rc["lineas"][0]
    assert linea["producto"]["id"] == p["id"]
    assert linea["cantidad_recibida"] == "10.000"
    assert linea["costo_unitario"] == {"monto": "2500.0000", "moneda": "COP"}
    assert linea["orden_linea_id"] is None
    assert linea["exceso"] is False
    assert rc["confirmada_en"] is None and rc["movimientos_generados"] == []


async def test_rf_com_005_recepcion_contra_orden_enlaza_las_lineas(
    cliente: httpx.AsyncClient,
) -> None:
    auth = await _sesion(cliente)
    prov = await _proveedor(cliente, auth)
    p1, p2 = await _producto(cliente, auth), await _producto(cliente, auth, nombre="Lápiz")
    orden = await _orden_emitida(
        cliente,
        auth,
        prov,
        [{"producto_id": p1["id"], "cantidad": "100"}, {"producto_id": p2["id"], "cantidad": "50"}],
    )
    r = await _recepcion(
        cliente,
        auth,
        proveedor_id=prov["id"],
        orden_id=orden["id"],
        lineas=[_linea(p1["id"], "60"), _linea(p2["id"], "50")],
    )
    assert r.status_code == 201, r.text
    rc = r.json()
    assert rc["orden"] == {"id": orden["id"], "numero": orden["numero"]}
    ids_orden = {linea["producto"]["id"]: linea["id"] for linea in orden["lineas"]}
    for linea in rc["lineas"]:
        assert linea["orden_linea_id"] == ids_orden[linea["producto"]["id"]]


async def test_rn_11_con_y_sin_orden_la_respuesta_tiene_la_misma_forma(
    cliente: httpx.AsyncClient,
) -> None:
    auth = await _sesion(cliente)
    prov = await _proveedor(cliente, auth)
    p = await _producto(cliente, auth)
    orden = await _orden_emitida(cliente, auth, prov, [{"producto_id": p["id"], "cantidad": "10"}])
    sin = (
        await _recepcion(cliente, auth, proveedor_id=prov["id"], lineas=[_linea(p["id"])])
    ).json()
    con = (
        await _recepcion(
            cliente, auth, proveedor_id=prov["id"], orden_id=orden["id"], lineas=[_linea(p["id"])]
        )
    ).json()
    assert set(sin) == set(con)
    assert set(sin["lineas"][0]) == set(con["lineas"][0])
    assert con["numero"] == "RC-000002"


async def test_rf_com_003_solo_se_recibe_contra_una_orden_emitida_o_parcial(
    cliente: httpx.AsyncClient,
) -> None:
    auth = await _sesion(cliente)
    prov = await _proveedor(cliente, auth)
    p = await _producto(cliente, auth)
    borrador = (
        await cliente.post(
            "/api/v1/ordenes-compra",
            json={
                "proveedor_id": prov["id"],
                "lineas": [{"producto_id": p["id"], "cantidad": "10"}],
            },
            headers=auth,
        )
    ).json()
    r = await _recepcion(
        cliente, auth, proveedor_id=prov["id"], orden_id=borrador["id"], lineas=[_linea(p["id"])]
    )
    assert r.status_code == 409
    assert r.json()["error"]["code"] == "ORDEN_NO_RECIBIBLE"


async def test_rf_com_005_el_proveedor_debe_ser_el_de_la_orden(cliente: httpx.AsyncClient) -> None:
    auth = await _sesion(cliente)
    prov, otro = await _proveedor(cliente, auth), await _proveedor(cliente, auth, "Otro")
    p = await _producto(cliente, auth)
    orden = await _orden_emitida(cliente, auth, prov, [{"producto_id": p["id"], "cantidad": "10"}])
    r = await _recepcion(
        cliente, auth, proveedor_id=otro["id"], orden_id=orden["id"], lineas=[_linea(p["id"])]
    )
    assert r.status_code == 422
    assert r.json()["error"]["code"] == "PROVEEDOR_NO_COINCIDE"


async def test_rf_com_005_un_producto_que_no_esta_en_la_orden_se_rechaza(
    cliente: httpx.AsyncClient,
) -> None:
    auth = await _sesion(cliente)
    prov = await _proveedor(cliente, auth)
    p, ajeno = await _producto(cliente, auth), await _producto(cliente, auth, nombre="Otro")
    orden = await _orden_emitida(cliente, auth, prov, [{"producto_id": p["id"], "cantidad": "10"}])
    r = await _recepcion(
        cliente, auth, proveedor_id=prov["id"], orden_id=orden["id"], lineas=[_linea(ajeno["id"])]
    )
    assert r.status_code == 422
    assert r.json()["error"]["code"] == "PRODUCTO_FUERA_DE_ORDEN"


async def test_rf_com_007_otra_moneda_exige_tasa_de_cambio(cliente: httpx.AsyncClient) -> None:
    auth = await _sesion(cliente)
    prov = await _proveedor(cliente, auth)
    p = await _producto(cliente, auth)
    sin_tasa = await _recepcion(
        cliente,
        auth,
        proveedor_id=prov["id"],
        moneda="USD",
        lineas=[_linea(p["id"], "10", "1.5", "USD")],
    )
    assert sin_tasa.status_code == 422
    assert sin_tasa.json()["error"]["code"] == "TASA_OBLIGATORIA"
    con_tasa = await _recepcion(
        cliente,
        auth,
        proveedor_id=prov["id"],
        moneda="USD",
        tasa_cambio="4100.5",
        lineas=[_linea(p["id"], "10", "1.5", "USD")],
    )
    assert con_tasa.status_code == 201, con_tasa.text
    assert con_tasa.json()["tasa_cambio"] == "4100.50000000"
    mezcla = await _recepcion(
        cliente,
        auth,
        proveedor_id=prov["id"],
        moneda="USD",
        tasa_cambio="4100",
        lineas=[_linea(p["id"], "10", "1.5", "COP")],
    )
    assert mezcla.status_code == 422 and mezcla.json()["error"]["code"] == "MONEDAS_DISTINTAS"


async def test_rf_com_004_el_borrador_se_edita_y_las_lineas_se_sustituyen(
    cliente: httpx.AsyncClient,
) -> None:
    auth = await _sesion(cliente)
    prov = await _proveedor(cliente, auth)
    p1, p2 = await _producto(cliente, auth), await _producto(cliente, auth, nombre="Lápiz")
    rc = (
        await _recepcion(cliente, auth, proveedor_id=prov["id"], lineas=[_linea(p1["id"])])
    ).json()
    r = await cliente.patch(
        f"/api/v1/recepciones/{rc['id']}",
        json={"lineas": [_linea(p2["id"], "3", "10")], "notas": "corregida"},
        headers=auth,
    )
    assert r.status_code == 200, r.text
    assert [linea["producto"]["id"] for linea in r.json()["lineas"]] == [p2["id"]]
    assert r.json()["notas"] == "corregida"


async def test_rf_com_004_una_recepcion_exige_lineas_con_cantidad_y_costo_validos(
    cliente: httpx.AsyncClient,
) -> None:
    auth = await _sesion(cliente)
    prov = await _proveedor(cliente, auth)
    p = await _producto(cliente, auth)
    assert (await _recepcion(cliente, auth, proveedor_id=prov["id"], lineas=[])).status_code == 422
    decimal = await _recepcion(
        cliente, auth, proveedor_id=prov["id"], lineas=[_linea(p["id"], "2.5")]
    )
    assert (
        decimal.status_code == 422
        and decimal.json()["error"]["code"] == "CANTIDAD_INVALIDA_PARA_UNIDAD"
    )
    sin_costo = await _recepcion(
        cliente, auth, proveedor_id=prov["id"], lineas=[{"producto_id": p["id"], "cantidad": "1"}]
    )
    assert sin_costo.status_code == 422


async def test_rn_17_un_proveedor_con_recepciones_no_se_borra(cliente: httpx.AsyncClient) -> None:
    auth = await _sesion(cliente)
    prov = await _proveedor(cliente, auth)
    p = await _producto(cliente, auth)
    await _recepcion(cliente, auth, proveedor_id=prov["id"], lineas=[_linea(p["id"])])
    r = await cliente.delete(f"/api/v1/proveedores/{prov['id']}", headers=auth)
    assert r.status_code == 409


async def test_rf_aut_007_la_recepcion_de_otro_negocio_no_existe(
    cliente: httpx.AsyncClient,
) -> None:
    a, b = await _sesion(cliente), await _sesion(cliente)
    prov = await _proveedor(cliente, a)
    p = await _producto(cliente, a)
    rc = (await _recepcion(cliente, a, proveedor_id=prov["id"], lineas=[_linea(p["id"])])).json()
    assert (await cliente.get(f"/api/v1/recepciones/{rc['id']}", headers=b)).status_code == 404
    assert (await cliente.get(f"/api/v1/recepciones/{uuid.uuid4()}", headers=a)).json()["error"][
        "code"
    ] == "RECEPCION_NO_ENCONTRADA"
