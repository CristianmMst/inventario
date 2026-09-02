"""RF-INT-001: eventos de catálogo, proveedores, compras, facturas y discrepancias."""

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


def _clave() -> dict[str, str]:
    return {"Idempotency-Key": str(uuid.uuid4())}


async def _eventos(cliente: httpx.AsyncClient, auth: dict, tipo: str) -> list[dict]:
    r = await cliente.get("/api/v1/eventos", params={"tipo": tipo, "limit": 100}, headers=auth)
    assert r.status_code == 200, r.text
    return r.json()["datos"]


async def _producto(cliente: httpx.AsyncClient, auth: dict, **extra: object) -> dict:
    cuerpo = {"nombre": "Cuaderno", "unidad_codigo": "unidad"} | extra
    return (await cliente.post("/api/v1/productos", json=cuerpo, headers=auth)).json()


async def _proveedor(cliente: httpx.AsyncClient, auth: dict) -> dict:
    r = await cliente.post(
        "/api/v1/proveedores",
        json={"nombre": "Papeles SAS", "identificacion_fiscal": "900-1", "contacto": "Julio"},
        headers=auth,
    )
    return r.json()


async def test_rf_int_001_producto_creado_actualizado_y_archivado(
    cliente: httpx.AsyncClient,
) -> None:
    auth = await _sesion(cliente)
    p = await _producto(cliente, auth, sku="C-1", codigos_barras=["7701"], stock_minimo="5")
    creado = (await _eventos(cliente, auth, "producto.creado"))[0]["payload"]
    assert creado["producto_id"] == p["id"] and creado["sku"] == "C-1"
    assert creado["codigos_barras"] == ["7701"] and creado["stock_minimo"] == "5.000"
    assert creado["unidad"] == "unidad" and creado["categoria"] is None

    await cliente.patch(
        f"/api/v1/productos/{p['id']}",
        json={"nombre": "Cuaderno rayado", "costo_actual": {"monto": "100", "moneda": "COP"}},
        headers=auth,
    )
    actualizado = (await _eventos(cliente, auth, "producto.actualizado"))[0]["payload"]
    assert actualizado["campos_cambiados"]["nombre"] == {
        "antes": "Cuaderno",
        "despues": "Cuaderno rayado",
    }
    assert actualizado["campos_cambiados"]["costo_actual"]["despues"] == {
        "monto": "100.0000",
        "moneda": "COP",
    }

    await cliente.post(f"/api/v1/productos/{p['id']}/archivar", headers=auth)
    archivado = (await _eventos(cliente, auth, "producto.archivado"))[0]["payload"]
    assert archivado == {
        "producto_id": p["id"],
        "nombre": "Cuaderno rayado",
        "stock_al_archivar": "0.000",
    }


async def test_rf_int_001_proveedor_creado(cliente: httpx.AsyncClient) -> None:
    auth = await _sesion(cliente)
    prov = await _proveedor(cliente, auth)
    e = (await _eventos(cliente, auth, "proveedor.creado"))[0]["payload"]
    assert e == {
        "proveedor_id": prov["id"],
        "nombre": "Papeles SAS",
        "identificacion_fiscal": "900-1",
        "contacto": "Julio",
    }


async def test_rf_int_001_compra_ordenada_recibida_parcial_y_cerrada_con_faltante(
    cliente: httpx.AsyncClient,
) -> None:
    auth = await _sesion(cliente)
    prov = await _proveedor(cliente, auth)
    p = await _producto(cliente, auth)
    o = (
        await cliente.post(
            "/api/v1/ordenes-compra",
            json={
                "proveedor_id": prov["id"],
                "fecha_esperada": "2026-09-15",
                "lineas": [
                    {
                        "producto_id": p["id"],
                        "cantidad": "100",
                        "costo_unitario_estimado": {"monto": "10", "moneda": "COP"},
                    }
                ],
            },
            headers=auth,
        )
    ).json()
    assert await _eventos(cliente, auth, "compra.ordenada") == [], "en borrador aún no es un hecho"
    await cliente.post(f"/api/v1/ordenes-compra/{o['id']}/emitir", headers=auth)
    ordenada = (await _eventos(cliente, auth, "compra.ordenada"))[0]["payload"]
    assert ordenada["orden_id"] == o["id"] and ordenada["fecha_esperada"] == "2026-09-15"
    assert ordenada["total_estimado"] == {"monto": "1000.0000", "moneda": "COP"}
    assert ordenada["lineas"][0]["cantidad"] == "100.000"

    rc = (
        await cliente.post(
            "/api/v1/recepciones",
            json={
                "proveedor_id": prov["id"],
                "orden_id": o["id"],
                "lineas": [
                    {
                        "producto_id": p["id"],
                        "cantidad": "60",
                        "costo_unitario": {"monto": "10", "moneda": "COP"},
                    }
                ],
            },
            headers=auth,
        )
    ).json()
    r = await cliente.post(
        f"/api/v1/recepciones/{rc['id']}/confirmar", json={}, headers=auth | _clave()
    )
    assert r.status_code == 200, r.text
    recibida = (await _eventos(cliente, auth, "compra.recibida"))[0]["payload"]
    assert recibida["recepcion_id"] == rc["id"] and recibida["orden_id"] == o["id"]
    assert recibida["total_moneda_base"] == {"monto": "600.0000", "moneda": "COP"}
    assert recibida["lineas"][0] == {
        "producto_id": p["id"],
        "cantidad": "60.000",
        "costo_unitario": {"monto": "10.0000", "moneda": "COP"},
        "costo_unitario_base": {"monto": "10.0000", "moneda": "COP"},
    }
    assert len(recibida["movimientos_generados"]) == 1
    parcial = (await _eventos(cliente, auth, "compra.recibida_parcial"))[0]["payload"]
    assert parcial["lineas_pendientes"] == [
        {"producto_id": p["id"], "cantidad_pendiente": "40.000"}
    ]
    # El movimiento de entrada de la recepción también emite movimiento.registrado con recepcion_id.
    movimientos = await _eventos(cliente, auth, "movimiento.registrado")
    assert movimientos[-1]["payload"]["recepcion_id"] == rc["id"]

    await cliente.post(
        f"/api/v1/ordenes-compra/{o['id']}/cerrar-con-faltante",
        json={"motivo": "Descontinuado"},
        headers=auth,
    )
    cerrada = (await _eventos(cliente, auth, "compra.cerrada_con_faltante"))[0]["payload"]
    assert cerrada == {
        "orden_id": o["id"],
        "motivo": "Descontinuado",
        "lineas_faltantes": [{"producto_id": p["id"], "cantidad_pendiente": "40.000"}],
    }


async def test_rf_int_001_factura_registrada_y_pagada(cliente: httpx.AsyncClient) -> None:
    auth = await _sesion(cliente)
    prov = await _proveedor(cliente, auth)
    f = (
        await cliente.post(
            "/api/v1/facturas",
            json={
                "proveedor_id": prov["id"],
                "numero": "F-1",
                "fecha_emision": "2026-09-01",
                "base_gravable": {"monto": "100", "moneda": "COP"},
                "impuesto": {"monto": "19", "moneda": "COP"},
                "total": {"monto": "119", "moneda": "COP"},
            },
            headers=auth | _clave(),
        )
    ).json()
    registrada = (await _eventos(cliente, auth, "factura.registrada"))[0]["payload"]
    assert registrada["factura_id"] == f["id"] and registrada["numero"] == "F-1"
    assert registrada["total"] == {"monto": "119.0000", "moneda": "COP"}
    assert (
        registrada["estado_pago"] == "pendiente"
        and registrada["recepciones"] == []
        and registrada["imagenes"] == []
    )
    await cliente.post(
        f"/api/v1/facturas/{f['id']}/pagar", json={"fecha_pago": "2026-09-10"}, headers=auth
    )
    pagada = (await _eventos(cliente, auth, "factura.pagada"))[0]["payload"]
    assert pagada == {
        "factura_id": f["id"],
        "proveedor_id": prov["id"],
        "numero": "F-1",
        "total": {"monto": "119.0000", "moneda": "COP"},
        "fecha_pago": "2026-09-10",
    }


async def test_rn_04_forzar_un_movimiento_emite_inventario_discrepancia(
    cliente: httpx.AsyncClient,
) -> None:
    auth = await _sesion(cliente)
    p = await _producto(cliente, auth)
    r = await cliente.post(
        "/api/v1/movimientos",
        json={
            "producto_id": p["id"],
            "tipo": "salida",
            "cantidad": "3",
            "motivo": "venta",
            "nota": "confirmado",
            "forzar": True,
        },
        headers=auth | _clave(),
    )
    assert r.status_code == 201, r.text
    d = (await _eventos(cliente, auth, "inventario.discrepancia"))[0]["payload"]
    assert d == {
        "movimiento_id": r.json()["id"],
        "producto_id": p["id"],
        "cantidad_solicitada": "3.000",
        "stock_disponible": "0.000",
        "stock_resultante": "-3.000",
        "motivo": "venta",
        "nota": "confirmado",
    }


async def test_rf_int_001_anular_emite_movimiento_anulado(cliente: httpx.AsyncClient) -> None:
    auth = await _sesion(cliente)
    p = await _producto(cliente, auth)
    m = (
        await cliente.post(
            "/api/v1/movimientos",
            json={
                "producto_id": p["id"],
                "tipo": "entrada",
                "cantidad": "5",
                "motivo": "carga_inicial",
            },
            headers=auth | _clave(),
        )
    ).json()
    contra = (
        await cliente.post(
            f"/api/v1/movimientos/{m['id']}/anular", json={"nota": "error"}, headers=auth | _clave()
        )
    ).json()
    e = (await _eventos(cliente, auth, "movimiento.anulado"))[0]["payload"]
    assert e == {
        "movimiento_id_original": m["id"],
        "contramovimiento_id": contra["id"],
        "producto_id": p["id"],
        "cantidad": "5.000",
        "motivo_anulacion": "error",
        "stock_resultante": "0.000",
    }
