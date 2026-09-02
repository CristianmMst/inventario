"""RF-COM-003 y RF-COM-010: transiciones de estado de la orden: emitir y cancelar.

Cancelar una orden con recepciones (409) se prueba en T-042, cuando existan recepciones.
"""

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


async def _orden(cliente: httpx.AsyncClient, auth: dict) -> dict:
    prov = (await cliente.post("/api/v1/proveedores", json={"nombre": "P"}, headers=auth)).json()
    prod = (
        await cliente.post(
            "/api/v1/productos",
            json={"nombre": "Cuaderno", "unidad_codigo": "unidad"},
            headers=auth,
        )
    ).json()
    r = await cliente.post(
        "/api/v1/ordenes-compra",
        json={
            "proveedor_id": prov["id"],
            "lineas": [{"producto_id": prod["id"], "cantidad": "10"}],
        },
        headers=auth,
    )
    assert r.status_code == 201, r.text
    return r.json() | {"producto_id": prod["id"]}


async def test_rf_com_003_emitir_pasa_de_borrador_a_emitida(cliente: httpx.AsyncClient) -> None:
    auth = await _sesion(cliente)
    o = await _orden(cliente, auth)
    r = await cliente.post(f"/api/v1/ordenes-compra/{o['id']}/emitir", headers=auth)
    assert r.status_code == 200, r.text
    assert r.json()["estado"] == "emitida"
    assert r.json()["emitida_en"] is not None


async def test_rf_com_003_una_emitida_no_admite_edicion_de_lineas(
    cliente: httpx.AsyncClient,
) -> None:
    auth = await _sesion(cliente)
    o = await _orden(cliente, auth)
    await cliente.post(f"/api/v1/ordenes-compra/{o['id']}/emitir", headers=auth)
    r = await cliente.patch(
        f"/api/v1/ordenes-compra/{o['id']}",
        json={"lineas": [{"producto_id": o["producto_id"], "cantidad": "5"}]},
        headers=auth,
    )
    assert r.status_code == 409
    assert r.json()["error"]["code"] == "ORDEN_NO_EDITABLE"
    solo_notas = await cliente.patch(
        f"/api/v1/ordenes-compra/{o['id']}", json={"notas": "aviso"}, headers=auth
    )
    assert solo_notas.status_code == 409


async def test_rf_com_003_emitir_dos_veces_responde_409(cliente: httpx.AsyncClient) -> None:
    auth = await _sesion(cliente)
    o = await _orden(cliente, auth)
    await cliente.post(f"/api/v1/ordenes-compra/{o['id']}/emitir", headers=auth)
    r = await cliente.post(f"/api/v1/ordenes-compra/{o['id']}/emitir", headers=auth)
    assert r.status_code == 409
    assert r.json()["error"]["code"] == "TRANSICION_INVALIDA"
    assert r.json()["error"]["details"]["estado"] == "emitida"


async def test_rf_com_010_una_emitida_sin_recepciones_se_cancela_con_motivo(
    cliente: httpx.AsyncClient,
) -> None:
    auth = await _sesion(cliente)
    o = await _orden(cliente, auth)
    await cliente.post(f"/api/v1/ordenes-compra/{o['id']}/emitir", headers=auth)
    r = await cliente.post(
        f"/api/v1/ordenes-compra/{o['id']}/cancelar",
        json={"motivo": "El proveedor no tiene existencias"},
        headers=auth,
    )
    assert r.status_code == 200, r.text
    assert r.json()["estado"] == "cancelada"
    assert r.json()["motivo_cierre"] == "El proveedor no tiene existencias"
    assert r.json()["cerrada_en"] is not None


async def test_rf_com_010_cancelar_exige_motivo(cliente: httpx.AsyncClient) -> None:
    auth = await _sesion(cliente)
    o = await _orden(cliente, auth)
    r = await cliente.post(f"/api/v1/ordenes-compra/{o['id']}/cancelar", json={}, headers=auth)
    assert r.status_code == 422
    vacio = await cliente.post(
        f"/api/v1/ordenes-compra/{o['id']}/cancelar", json={"motivo": "  "}, headers=auth
    )
    assert vacio.status_code == 422


async def test_rf_com_010_una_cancelada_no_se_emite_ni_se_cancela_otra_vez(
    cliente: httpx.AsyncClient,
) -> None:
    auth = await _sesion(cliente)
    o = await _orden(cliente, auth)
    await cliente.post(
        f"/api/v1/ordenes-compra/{o['id']}/cancelar", json={"motivo": "duplicada"}, headers=auth
    )
    assert (
        await cliente.post(f"/api/v1/ordenes-compra/{o['id']}/emitir", headers=auth)
    ).status_code == 409
    assert (
        await cliente.post(
            f"/api/v1/ordenes-compra/{o['id']}/cancelar", json={"motivo": "otra"}, headers=auth
        )
    ).status_code == 409


async def test_rf_com_013_el_listado_filtra_por_estado(cliente: httpx.AsyncClient) -> None:
    auth = await _sesion(cliente)
    borrador = await _orden(cliente, auth)
    emitida = await _orden(cliente, auth)
    await cliente.post(f"/api/v1/ordenes-compra/{emitida['id']}/emitir", headers=auth)
    r = await cliente.get("/api/v1/ordenes-compra", params={"estado": "emitida"}, headers=auth)
    assert [o["id"] for o in r.json()["datos"]] == [emitida["id"]]
    todas = await cliente.get("/api/v1/ordenes-compra", headers=auth)
    assert [o["id"] for o in todas.json()["datos"]] == [emitida["id"], borrador["id"]]


async def test_rf_aut_007_emitir_una_orden_ajena_responde_404(cliente: httpx.AsyncClient) -> None:
    a, b = await _sesion(cliente), await _sesion(cliente)
    o = await _orden(cliente, a)
    assert (
        await cliente.post(f"/api/v1/ordenes-compra/{o['id']}/emitir", headers=b)
    ).status_code == 404
