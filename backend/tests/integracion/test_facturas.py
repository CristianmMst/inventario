"""RF-FAC-001..004 y RN-18: facturas de compra; cuadre exacto y número único por proveedor."""

import uuid

import httpx
import pytest
import sqlalchemy as sa
from sqlalchemy.exc import IntegrityError
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


async def _proveedor(cliente: httpx.AsyncClient, auth: dict, nombre: str = "Papeles SAS") -> dict:
    return (await cliente.post("/api/v1/proveedores", json={"nombre": nombre}, headers=auth)).json()


def _cuerpo(proveedor_id: str, **extra: object) -> dict:
    base = {
        "proveedor_id": proveedor_id,
        "numero": "FV-1001",
        "fecha_emision": "2026-09-01",
        "fecha_vencimiento": "2026-10-01",
        "base_gravable": {"monto": "100000", "moneda": "COP"},
        "impuesto": {"monto": "19000", "moneda": "COP"},
        "total": {"monto": "119000", "moneda": "COP"},
        "notas": "Compra de septiembre",
    }
    return base | extra


async def _factura(cliente: httpx.AsyncClient, auth: dict, cuerpo: dict) -> httpx.Response:
    return await cliente.post("/api/v1/facturas", json=cuerpo, headers=auth | _clave())


async def test_rf_fac_001_registro_de_factura_de_compra(cliente: httpx.AsyncClient) -> None:
    auth = await _sesion(cliente)
    prov = await _proveedor(cliente, auth)
    r = await _factura(cliente, auth, _cuerpo(prov["id"]))
    assert r.status_code == 201, r.text
    f = r.json()
    assert f["proveedor"] == {"id": prov["id"], "nombre": "Papeles SAS"}
    assert f["numero"] == "FV-1001"
    assert f["fecha_emision"] == "2026-09-01" and f["fecha_vencimiento"] == "2026-10-01"
    assert f["moneda"] == "COP"
    assert f["base_gravable"] == {"monto": "100000.0000", "moneda": "COP"}
    assert f["impuesto"] == {"monto": "19000.0000", "moneda": "COP"}
    assert f["total"] == {"monto": "119000.0000", "moneda": "COP"}
    assert f["total_base"] == {"monto": "119000.0000", "moneda": "COP"}
    assert f["estado_pago"] == "pendiente" and f["fecha_pago"] is None
    assert f["recepciones"] == [] and f["imagenes"] == []
    ficha = await cliente.get(f"/api/v1/facturas/{f['id']}", headers=auth)
    assert ficha.status_code == 200 and ficha.json()["numero"] == "FV-1001"


async def test_rn_18_base_mas_impuesto_distinto_del_total_responde_422_mostrando_la_diferencia(
    cliente: httpx.AsyncClient,
) -> None:
    auth = await _sesion(cliente)
    prov = await _proveedor(cliente, auth)
    r = await _factura(
        cliente, auth, _cuerpo(prov["id"], total={"monto": "119001", "moneda": "COP"})
    )
    assert r.status_code == 422, r.text
    error = r.json()["error"]
    assert error["code"] == "FACTURA_NO_CUADRA"
    assert error["details"]["diferencia"] == "1.0000"
    assert error["details"]["suma"] == "119000.0000" and error["details"]["total"] == "119001.0000"


async def test_rn_18_la_base_rechaza_el_descuadre_aunque_pydantic_no(sesion: AsyncSession) -> None:
    from app.modelos.compras import Proveedor
    from app.modelos.identidad import Negocio

    negocio = Negocio(nombre="P", moneda_base="COP", zona_horaria="UTC")
    sesion.add(negocio)
    await sesion.flush()
    prov = Proveedor(negocio_id=negocio.id, nombre="P")
    sesion.add(prov)
    await sesion.flush()
    with pytest.raises(IntegrityError) as info:
        await sesion.execute(
            sa.text(
                "insert into facturas (negocio_id, proveedor_id, numero, fecha_emision, moneda,"
                " base_gravable, impuesto, total, tasa_cambio, total_base)"
                " values (:n, :p, 'X', current_date, 'COP', 100, 19, 120, 1, 120)"
            ),
            {"n": negocio.id, "p": prov.id},
        )
    assert "ck_facturas_cuadre" in str(info.value)
    await sesion.rollback()


async def test_rf_fac_002_numero_repetido_del_mismo_proveedor_responde_409_indicando_la_existente(
    cliente: httpx.AsyncClient,
) -> None:
    auth = await _sesion(cliente)
    prov = await _proveedor(cliente, auth)
    primera = (await _factura(cliente, auth, _cuerpo(prov["id"]))).json()
    repetida = await _factura(cliente, auth, _cuerpo(prov["id"], notas="otra"))
    assert repetida.status_code == 409
    error = repetida.json()["error"]
    assert error["code"] == "FACTURA_DUPLICADA"
    assert error["details"]["factura_id"] == primera["id"]
    assert error["details"]["numero"] == "FV-1001"


async def test_rf_fac_002_el_mismo_numero_de_otro_proveedor_se_acepta(
    cliente: httpx.AsyncClient,
) -> None:
    auth = await _sesion(cliente)
    a, b = await _proveedor(cliente, auth, "A"), await _proveedor(cliente, auth, "B")
    assert (await _factura(cliente, auth, _cuerpo(a["id"]))).status_code == 201
    assert (await _factura(cliente, auth, _cuerpo(b["id"]))).status_code == 201


async def test_rf_fac_003_otra_moneda_registra_tasa_y_total_en_moneda_base(
    cliente: httpx.AsyncClient,
) -> None:
    auth = await _sesion(cliente)
    prov = await _proveedor(cliente, auth)
    r = await _factura(
        cliente,
        auth,
        _cuerpo(
            prov["id"],
            moneda="USD",
            tasa_cambio="4000",
            base_gravable={"monto": "100", "moneda": "USD"},
            impuesto={"monto": "19", "moneda": "USD"},
            total={"monto": "119", "moneda": "USD"},
        ),
    )
    assert r.status_code == 201, r.text
    assert r.json()["tasa_cambio"] == "4000.00000000"
    assert r.json()["total_base"] == {"monto": "476000.0000", "moneda": "COP"}
    sin_tasa = await _factura(
        cliente,
        auth,
        _cuerpo(
            prov["id"],
            numero="FV-2",
            moneda="USD",
            base_gravable={"monto": "100", "moneda": "USD"},
            impuesto={"monto": "19", "moneda": "USD"},
            total={"monto": "119", "moneda": "USD"},
        ),
    )
    assert sin_tasa.status_code == 422 and sin_tasa.json()["error"]["code"] == "TASA_OBLIGATORIA"


async def test_rf_fac_003_los_importes_deben_ir_en_la_moneda_de_la_factura(
    cliente: httpx.AsyncClient,
) -> None:
    auth = await _sesion(cliente)
    prov = await _proveedor(cliente, auth)
    r = await _factura(
        cliente, auth, _cuerpo(prov["id"], total={"monto": "119000", "moneda": "USD"})
    )
    assert r.status_code == 422
    assert r.json()["error"]["code"] == "MONEDAS_DISTINTAS"


async def test_rf_fac_004_marcar_como_pagada_exige_fecha(cliente: httpx.AsyncClient) -> None:
    auth = await _sesion(cliente)
    prov = await _proveedor(cliente, auth)
    f = (await _factura(cliente, auth, _cuerpo(prov["id"]))).json()
    sin_fecha = await cliente.post(f"/api/v1/facturas/{f['id']}/pagar", json={}, headers=auth)
    assert sin_fecha.status_code == 422
    pagada = await cliente.post(
        f"/api/v1/facturas/{f['id']}/pagar", json={"fecha_pago": "2026-09-15"}, headers=auth
    )
    assert pagada.status_code == 200, pagada.text
    assert pagada.json()["estado_pago"] == "pagada" and pagada.json()["fecha_pago"] == "2026-09-15"
    otra_vez = await cliente.post(
        f"/api/v1/facturas/{f['id']}/pagar", json={"fecha_pago": "2026-09-16"}, headers=auth
    )
    assert otra_vez.status_code == 409
    assert otra_vez.json()["error"]["code"] == "FACTURA_YA_PAGADA"


async def test_rf_fac_004_una_pagada_desaparece_del_filtro_de_pendientes(
    cliente: httpx.AsyncClient,
) -> None:
    auth = await _sesion(cliente)
    prov = await _proveedor(cliente, auth)
    pendiente = (await _factura(cliente, auth, _cuerpo(prov["id"], numero="P-1"))).json()
    pagada = (await _factura(cliente, auth, _cuerpo(prov["id"], numero="P-2"))).json()
    await cliente.post(
        f"/api/v1/facturas/{pagada['id']}/pagar", json={"fecha_pago": "2026-09-15"}, headers=auth
    )
    r = await cliente.get("/api/v1/facturas", params={"estado_pago": "pendiente"}, headers=auth)
    assert [x["id"] for x in r.json()["datos"]] == [pendiente["id"]]
    todas = await cliente.get("/api/v1/facturas", headers=auth)
    assert {x["id"] for x in todas.json()["datos"]} == {pendiente["id"], pagada["id"]}


async def test_rf_fac_004_anular_una_factura(cliente: httpx.AsyncClient) -> None:
    auth = await _sesion(cliente)
    prov = await _proveedor(cliente, auth)
    f = (await _factura(cliente, auth, _cuerpo(prov["id"]))).json()
    r = await cliente.post(
        f"/api/v1/facturas/{f['id']}/anular", json={"motivo": "Emitida por error"}, headers=auth
    )
    assert r.status_code == 200, r.text
    assert r.json()["estado_pago"] == "anulada"
    pagar = await cliente.post(
        f"/api/v1/facturas/{f['id']}/pagar", json={"fecha_pago": "2026-09-15"}, headers=auth
    )
    assert pagar.status_code == 409


async def test_rn_20_registrar_factura_es_idempotente(
    cliente: httpx.AsyncClient, sesion: AsyncSession
) -> None:
    auth = await _sesion(cliente)
    prov = await _proveedor(cliente, auth)
    clave = _clave()
    a = await cliente.post("/api/v1/facturas", json=_cuerpo(prov["id"]), headers=auth | clave)
    b = await cliente.post("/api/v1/facturas", json=_cuerpo(prov["id"]), headers=auth | clave)
    assert a.status_code == b.status_code == 201
    assert a.json()["id"] == b.json()["id"]
    assert (await sesion.execute(sa.text("select count(*) from facturas"))).scalar_one() == 1


async def test_rf_fac_001_editar_notas_y_vencimiento(cliente: httpx.AsyncClient) -> None:
    auth = await _sesion(cliente)
    prov = await _proveedor(cliente, auth)
    f = (await _factura(cliente, auth, _cuerpo(prov["id"]))).json()
    r = await cliente.patch(
        f"/api/v1/facturas/{f['id']}",
        json={"notas": "Vence más tarde", "fecha_vencimiento": "2026-11-01"},
        headers=auth,
    )
    assert r.status_code == 200
    assert r.json()["notas"] == "Vence más tarde" and r.json()["fecha_vencimiento"] == "2026-11-01"


async def test_rn_17_un_proveedor_con_facturas_no_se_borra(cliente: httpx.AsyncClient) -> None:
    auth = await _sesion(cliente)
    prov = await _proveedor(cliente, auth)
    await _factura(cliente, auth, _cuerpo(prov["id"]))
    assert (
        await cliente.delete(f"/api/v1/proveedores/{prov['id']}", headers=auth)
    ).status_code == 409


async def test_rf_aut_007_la_factura_de_otro_negocio_no_existe(cliente: httpx.AsyncClient) -> None:
    a, b = await _sesion(cliente), await _sesion(cliente)
    prov = await _proveedor(cliente, a)
    f = (await _factura(cliente, a, _cuerpo(prov["id"]))).json()
    assert (await cliente.get(f"/api/v1/facturas/{f['id']}", headers=b)).status_code == 404
    assert (await cliente.get(f"/api/v1/facturas/{uuid.uuid4()}", headers=a)).json()["error"][
        "code"
    ] == "FACTURA_NO_ENCONTRADA"
