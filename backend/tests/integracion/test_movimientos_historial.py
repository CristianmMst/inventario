"""RF-INV-012, RF-INV-014, RF-REP-004, RF-INV-002: historial paginado con stock resultante."""

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


async def _producto(cliente: httpx.AsyncClient, auth: dict, nombre: str = "Cuaderno") -> dict:
    r = await cliente.post(
        "/api/v1/productos", json={"nombre": nombre, "unidad_codigo": "unidad"}, headers=auth
    )
    return r.json()


async def _mover(cliente: httpx.AsyncClient, auth: dict, **cuerpo: object) -> dict:
    r = await cliente.post(
        "/api/v1/movimientos", json=cuerpo, headers=auth | {"Idempotency-Key": str(uuid.uuid4())}
    )
    assert r.status_code == 201, r.text
    return r.json()


async def _historial(
    cliente: httpx.AsyncClient, auth: dict, producto_id: str, **params: object
) -> dict:
    r = await cliente.get(
        f"/api/v1/productos/{producto_id}/movimientos", params=params, headers=auth
    )
    assert r.status_code == 200, r.text
    return r.json()


async def test_rf_inv_012_orden_cronologico_inverso_con_stock_resultante_en_cada_fila(
    cliente: httpx.AsyncClient,
) -> None:
    auth = await _sesion(cliente)
    p = await _producto(cliente, auth)
    await _mover(
        cliente, auth, producto_id=p["id"], tipo="entrada", cantidad="10", motivo="carga_inicial"
    )
    await _mover(cliente, auth, producto_id=p["id"], tipo="salida", cantidad="3", motivo="venta")
    await _mover(cliente, auth, producto_id=p["id"], tipo="salida", cantidad="2", motivo="venta")
    filas = (await _historial(cliente, auth, p["id"]))["datos"]
    assert [f["stock_resultante"] for f in filas] == ["5.000", "7.000", "10.000"]
    assert [f["tipo"] for f in filas] == ["salida", "salida", "entrada"]
    momentos = [f["ocurrido_en"] for f in filas]
    assert momentos == sorted(momentos, reverse=True)


async def test_rf_inv_012_el_anulado_y_su_contramovimiento_se_ven_marcados(
    cliente: httpx.AsyncClient,
) -> None:
    auth = await _sesion(cliente)
    p = await _producto(cliente, auth)
    entrada = await _mover(
        cliente, auth, producto_id=p["id"], tipo="entrada", cantidad="10", motivo="carga_inicial"
    )
    salida = await _mover(
        cliente, auth, producto_id=p["id"], tipo="salida", cantidad="3", motivo="venta"
    )
    r = await cliente.post(
        f"/api/v1/movimientos/{salida['id']}/anular",
        json={"nota": "error"},
        headers=auth | {"Idempotency-Key": str(uuid.uuid4())},
    )
    assert r.status_code == 201
    filas = {f["id"]: f for f in (await _historial(cliente, auth, p["id"]))["datos"]}
    assert filas[salida["id"]]["anulado_en"] is not None
    assert filas[entrada["id"]]["anulado_en"] is None
    contra = filas[r.json()["id"]]
    assert contra["tipo"] == "contramovimiento" and contra["anula_movimiento_id"] == salida["id"]


async def test_rf_inv_012_el_cursor_es_estable_aunque_lleguen_movimientos_nuevos(
    cliente: httpx.AsyncClient,
) -> None:
    auth = await _sesion(cliente)
    p = await _producto(cliente, auth)
    ids = [
        (
            await _mover(
                cliente,
                auth,
                producto_id=p["id"],
                tipo="entrada",
                cantidad="1",
                motivo="carga_inicial",
            )
        )["id"]
        for _ in range(5)
    ]
    primera = await _historial(cliente, auth, p["id"], limit=2)
    assert primera["tiene_mas"]
    # Llega un movimiento nuevo entre páginas: es el más reciente, así que no debe colarse.
    nuevo = await _mover(
        cliente, auth, producto_id=p["id"], tipo="entrada", cantidad="1", motivo="carga_inicial"
    )
    vistos = [f["id"] for f in primera["datos"]]
    pagina = primera
    while pagina["tiene_mas"]:
        pagina = await _historial(
            cliente, auth, p["id"], limit=2, cursor=pagina["cursor_siguiente"]
        )
        vistos += [f["id"] for f in pagina["datos"]]
    assert len(vistos) == len(set(vistos)) == 5
    assert set(vistos) == set(ids)
    assert nuevo["id"] not in vistos


async def test_rf_inv_002_get_movimientos_filtra_por_producto_y_tipo(
    cliente: httpx.AsyncClient,
) -> None:
    auth = await _sesion(cliente)
    a = await _producto(cliente, auth, "A")
    b = await _producto(cliente, auth, "B")
    await _mover(
        cliente, auth, producto_id=a["id"], tipo="entrada", cantidad="5", motivo="carga_inicial"
    )
    await _mover(cliente, auth, producto_id=a["id"], tipo="salida", cantidad="1", motivo="venta")
    await _mover(
        cliente, auth, producto_id=b["id"], tipo="entrada", cantidad="2", motivo="carga_inicial"
    )

    todos = await cliente.get("/api/v1/movimientos", headers=auth)
    assert todos.status_code == 200 and len(todos.json()["datos"]) == 3
    de_a = await cliente.get("/api/v1/movimientos", params={"producto_id": a["id"]}, headers=auth)
    assert len(de_a.json()["datos"]) == 2
    salidas = await cliente.get("/api/v1/movimientos", params={"tipo": "salida"}, headers=auth)
    assert [f["producto_id"] for f in salidas.json()["datos"]] == [a["id"]]


async def test_rf_inv_002_get_movimientos_filtra_por_rango_de_fechas(
    cliente: httpx.AsyncClient,
) -> None:
    auth = await _sesion(cliente)
    p = await _producto(cliente, auth)
    m = await _mover(
        cliente, auth, producto_id=p["id"], tipo="entrada", cantidad="5", motivo="carga_inicial"
    )
    dentro = await cliente.get(
        "/api/v1/movimientos",
        params={"desde": "2026-01-01T00:00:00Z", "hasta": "2100-01-01T00:00:00Z"},
        headers=auth,
    )
    fuera = await cliente.get(
        "/api/v1/movimientos",
        params={"desde": "2000-01-01T00:00:00Z", "hasta": "2001-01-01T00:00:00Z"},
        headers=auth,
    )
    assert [f["id"] for f in dentro.json()["datos"]] == [m["id"]]
    assert fuera.json()["datos"] == []


async def test_rf_cat_011_un_producto_archivado_conserva_su_historial(
    cliente: httpx.AsyncClient,
) -> None:
    auth = await _sesion(cliente)
    p = await _producto(cliente, auth)
    await _mover(
        cliente, auth, producto_id=p["id"], tipo="entrada", cantidad="5", motivo="carga_inicial"
    )
    await cliente.post(f"/api/v1/productos/{p['id']}/archivar", headers=auth)
    filas = (await _historial(cliente, auth, p["id"]))["datos"]
    assert len(filas) == 1


async def test_rf_aut_007_el_historial_de_un_producto_ajeno_no_existe(
    cliente: httpx.AsyncClient,
) -> None:
    a, b = await _sesion(cliente), await _sesion(cliente)
    p = await _producto(cliente, a)
    r = await cliente.get(f"/api/v1/productos/{p['id']}/movimientos", headers=b)
    assert r.status_code == 404
    assert (await cliente.get("/api/v1/movimientos", headers=b)).json()["datos"] == []


async def test_rf_rep_004_el_historial_completo_se_exporta_recorriendo_el_cursor(
    cliente: httpx.AsyncClient,
) -> None:
    """RF-REP-004: el historial por producto es exportable: paginado, un consumidor lo recorre
    entero sin saltos ni repeticiones y cada fila lleva su stock resultante."""
    auth = await _sesion(cliente)
    p = await _producto(cliente, auth)
    for _ in range(7):
        await _mover(
            cliente, auth, producto_id=p["id"], tipo="entrada", cantidad="1", motivo="carga_inicial"
        )
    filas: list[dict] = []
    pagina = await _historial(cliente, auth, p["id"], limit=3)
    filas += pagina["datos"]
    while pagina["tiene_mas"]:
        pagina = await _historial(
            cliente, auth, p["id"], limit=3, cursor=pagina["cursor_siguiente"]
        )
        filas += pagina["datos"]
    assert len(filas) == 7 and len({f["id"] for f in filas}) == 7
    assert [f["stock_resultante"] for f in filas] == [f"{n}.000" for n in range(7, 0, -1)]
