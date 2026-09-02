"""RF-FAC-005 (imágenes), RF-FAC-006 (recepciones) y RF-FAC-008 (listado con total del filtro)."""

import io
import uuid

import httpx
import sqlalchemy as sa
from PIL import Image
from sqlalchemy.ext.asyncio import AsyncSession

from tests import fabricas


def _jpeg(ancho: int = 200, alto: int = 200, calidad: int = 80) -> bytes:
    buffer = io.BytesIO()
    Image.effect_noise((ancho, alto), 64).convert("RGB").save(
        buffer, format="JPEG", quality=calidad
    )
    return buffer.getvalue()


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


async def _factura(
    cliente: httpx.AsyncClient,
    auth: dict,
    prov: dict,
    numero: str = "F-1",
    total: str = "119000",
    **extra: object,
) -> dict:
    base = str(int(total) - int(total) // 119 * 19) if total.isdigit() else "100000"
    imp = str(int(total) - int(base)) if total.isdigit() else "19000"
    cuerpo = {
        "proveedor_id": prov["id"],
        "numero": numero,
        "fecha_emision": "2026-09-01",
        "base_gravable": {"monto": base, "moneda": "COP"},
        "impuesto": {"monto": imp, "moneda": "COP"},
        "total": {"monto": total, "moneda": "COP"},
    } | extra
    r = await cliente.post("/api/v1/facturas", json=cuerpo, headers=auth | _clave())
    assert r.status_code == 201, r.text
    return r.json()


async def _recepcion_confirmada(cliente: httpx.AsyncClient, auth: dict, prov: dict) -> dict:
    p = (
        await cliente.post(
            "/api/v1/productos",
            json={"nombre": "Cuaderno", "unidad_codigo": "unidad"},
            headers=auth,
        )
    ).json()
    rc = await cliente.post(
        "/api/v1/recepciones",
        json={
            "proveedor_id": prov["id"],
            "lineas": [
                {
                    "producto_id": p["id"],
                    "cantidad": "5",
                    "costo_unitario": {"monto": "10", "moneda": "COP"},
                }
            ],
        },
        headers=auth,
    )
    r = await cliente.post(
        f"/api/v1/recepciones/{rc.json()['id']}/confirmar", json={}, headers=auth | _clave()
    )
    assert r.status_code == 200, r.text
    return r.json()


async def _adjuntar(
    cliente: httpx.AsyncClient, auth: dict, factura_id: str, contenido: bytes
) -> httpx.Response:
    return await cliente.post(
        f"/api/v1/facturas/{factura_id}/imagenes",
        files={"archivo": ("factura.jpg", contenido, "image/jpeg")},
        headers=auth,
    )


async def test_rf_fac_005_varias_imagenes_por_factura_ordenadas(cliente: httpx.AsyncClient) -> None:
    auth = await _sesion(cliente)
    prov = await _proveedor(cliente, auth)
    f = await _factura(cliente, auth, prov)
    primera = await _adjuntar(cliente, auth, f["id"], _jpeg(300, 400))
    assert primera.status_code == 201, primera.text
    segunda = await _adjuntar(cliente, auth, f["id"], _jpeg(500, 300))
    assert segunda.status_code == 201
    imagenes = segunda.json()["imagenes"]
    assert [i["ancho"] for i in imagenes] == [300, 500]
    assert all(i["url"].startswith("/api/v1/imagenes/") for i in imagenes)
    ficha = await cliente.get(f"/api/v1/facturas/{f['id']}", headers=auth)
    assert [i["id"] for i in ficha.json()["imagenes"]] == [i["id"] for i in imagenes]


async def test_rf_fac_005_quitar_una_imagen(
    cliente: httpx.AsyncClient, sesion: AsyncSession
) -> None:
    auth = await _sesion(cliente)
    prov = await _proveedor(cliente, auth)
    f = await _factura(cliente, auth, prov)
    a = (await _adjuntar(cliente, auth, f["id"], _jpeg())).json()["imagenes"][0]
    b = (await _adjuntar(cliente, auth, f["id"], _jpeg())).json()["imagenes"][1]
    r = await cliente.delete(f"/api/v1/facturas/{f['id']}/imagenes/{a['id']}", headers=auth)
    assert r.status_code == 200, r.text
    assert [i["id"] for i in r.json()["imagenes"]] == [b["id"]]
    quedan = (await sesion.execute(sa.text("select count(*) from imagenes"))).scalar_one()
    assert quedan == 1
    otra_vez = await cliente.delete(f"/api/v1/facturas/{f['id']}/imagenes/{a['id']}", headers=auth)
    assert otra_vez.status_code == 404


async def test_rnf_05_los_limites_de_factura_son_mas_amplios_que_los_de_producto(
    cliente: httpx.AsyncClient,
) -> None:
    auth = await _sesion(cliente)
    prov = await _proveedor(cliente, auth)
    f = await _factura(cliente, auth, prov)
    grande_para_producto = _jpeg(1900, 1400)
    assert len(grande_para_producto) > 300 * 1024 or max(1900, 1400) > 1280
    r = await _adjuntar(cliente, auth, f["id"], grande_para_producto)
    assert r.status_code == 201, r.text
    demasiado = await _adjuntar(cliente, auth, f["id"], _jpeg(2049, 100))
    assert demasiado.status_code == 422
    assert demasiado.json()["error"]["code"] == "IMAGEN_DEMASIADO_GRANDE"


async def test_rf_fac_006_una_recepcion_no_puede_estar_en_dos_facturas(
    cliente: httpx.AsyncClient,
) -> None:
    auth = await _sesion(cliente)
    prov = await _proveedor(cliente, auth)
    rc = await _recepcion_confirmada(cliente, auth, prov)
    f1 = await _factura(cliente, auth, prov, numero="F-1", recepciones=[rc["id"]])
    assert [r["id"] for r in f1["recepciones"]] == [rc["id"]]
    assert f1["recepciones"][0]["numero"] == rc["numero"]
    cuerpo = {
        "proveedor_id": prov["id"],
        "numero": "F-2",
        "fecha_emision": "2026-09-01",
        "base_gravable": {"monto": "100", "moneda": "COP"},
        "impuesto": {"monto": "0", "moneda": "COP"},
        "total": {"monto": "100", "moneda": "COP"},
        "recepciones": [rc["id"]],
    }
    f2 = await cliente.post("/api/v1/facturas", json=cuerpo, headers=auth | _clave())
    assert f2.status_code == 409
    assert f2.json()["error"]["code"] == "RECEPCION_YA_FACTURADA"
    assert f2.json()["error"]["details"]["factura_id"] == f1["id"]


async def test_rf_fac_006_se_admite_factura_sin_recepcion_y_vincular_despues(
    cliente: httpx.AsyncClient,
) -> None:
    auth = await _sesion(cliente)
    prov = await _proveedor(cliente, auth)
    f = await _factura(cliente, auth, prov)
    assert f["recepciones"] == []
    rc = await _recepcion_confirmada(cliente, auth, prov)
    r = await cliente.put(
        f"/api/v1/facturas/{f['id']}/recepciones", json={"recepciones": [rc["id"]]}, headers=auth
    )
    assert r.status_code == 200, r.text
    assert [x["id"] for x in r.json()["recepciones"]] == [rc["id"]]
    vacio = await cliente.put(
        f"/api/v1/facturas/{f['id']}/recepciones", json={"recepciones": []}, headers=auth
    )
    assert vacio.json()["recepciones"] == []


async def test_rf_fac_006_la_recepcion_debe_ser_del_mismo_proveedor(
    cliente: httpx.AsyncClient,
) -> None:
    auth = await _sesion(cliente)
    a, b = await _proveedor(cliente, auth, "A"), await _proveedor(cliente, auth, "B")
    rc_b = await _recepcion_confirmada(cliente, auth, b)
    f = await _factura(cliente, auth, a)
    r = await cliente.put(
        f"/api/v1/facturas/{f['id']}/recepciones", json={"recepciones": [rc_b["id"]]}, headers=auth
    )
    assert r.status_code == 422
    assert r.json()["error"]["code"] == "RECEPCION_DE_OTRO_PROVEEDOR"


async def test_rf_fac_008_el_total_corresponde_al_filtro_no_a_la_pagina(
    cliente: httpx.AsyncClient,
) -> None:
    auth = await _sesion(cliente)
    a, b = await _proveedor(cliente, auth, "A"), await _proveedor(cliente, auth, "B")
    for i, total in enumerate(["119", "238", "357"], start=1):
        await _factura(cliente, auth, a, numero=f"A-{i}", total=total)
    await _factura(cliente, auth, b, numero="B-1", total="1190")
    pagada = await _factura(cliente, auth, a, numero="A-9", total="595")
    await cliente.post(
        f"/api/v1/facturas/{pagada['id']}/pagar", json={"fecha_pago": "2026-09-02"}, headers=auth
    )

    r = await cliente.get(
        "/api/v1/facturas", params={"proveedor_id": a["id"], "limit": 2}, headers=auth
    )
    assert r.status_code == 200, r.text
    datos = r.json()
    assert len(datos["datos"]) == 2 and datos["tiene_mas"]
    assert datos["cantidad_filtro"] == 4
    assert datos["total_filtro"] == {"monto": "1309.0000", "moneda": "COP"}

    pendientes = await cliente.get(
        "/api/v1/facturas",
        params={"proveedor_id": a["id"], "estado_pago": "pendiente"},
        headers=auth,
    )
    assert pendientes.json()["cantidad_filtro"] == 3
    assert pendientes.json()["total_filtro"]["monto"] == "714.0000"
    por_fecha = await cliente.get("/api/v1/facturas", params={"desde": "2030-01-01"}, headers=auth)
    assert (
        por_fecha.json()["cantidad_filtro"] == 0
        and por_fecha.json()["total_filtro"]["monto"] == "0.0000"
    )
