"""RF-FAC-007: exportación ZIP con CSV e imágenes nombradas AAAA-MM-DD_proveedor_numero.jpg."""

import csv
import io
import uuid
import zipfile

import httpx
from PIL import Image

from tests import fabricas


def _jpeg() -> bytes:
    buffer = io.BytesIO()
    Image.new("RGB", (120, 90), (0, 0, 200)).save(buffer, format="JPEG")
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


async def _factura(
    cliente: httpx.AsyncClient, auth: dict, prov: dict, numero: str, fecha: str, imagenes: int
) -> dict:
    cuerpo = {
        "proveedor_id": prov["id"],
        "numero": numero,
        "fecha_emision": fecha,
        "base_gravable": {"monto": "100", "moneda": "COP"},
        "impuesto": {"monto": "19", "moneda": "COP"},
        "total": {"monto": "119", "moneda": "COP"},
    }
    f = (await cliente.post("/api/v1/facturas", json=cuerpo, headers=auth | _clave())).json()
    for _ in range(imagenes):
        r = await cliente.post(
            f"/api/v1/facturas/{f['id']}/imagenes",
            files={"archivo": ("f.jpg", _jpeg(), "image/jpeg")},
            headers=auth,
        )
        assert r.status_code == 201, r.text
    return f


async def test_rf_fac_007_el_zip_abre_fuera_de_la_app_y_el_csv_cuadra_con_las_imagenes(
    cliente: httpx.AsyncClient,
) -> None:
    auth = await _sesion(cliente)
    prov = (
        await cliente.post(
            "/api/v1/proveedores", json={"nombre": "Papeles & Cía. S.A.S"}, headers=auth
        )
    ).json()
    dentro_1 = await _factura(cliente, auth, prov, "FV-001", "2026-09-05", imagenes=2)
    dentro_2 = await _factura(cliente, auth, prov, "FV-002/A", "2026-09-20", imagenes=1)
    await _factura(cliente, auth, prov, "FV-003", "2026-10-01", imagenes=1)  # fuera del período

    r = await cliente.get(
        "/api/v1/facturas/exportacion",
        params={"desde": "2026-09-01", "hasta": "2026-09-30"},
        headers=auth,
    )
    assert r.status_code == 200, r.text
    assert r.headers["content-type"] == "application/zip"
    assert "attachment" in r.headers["content-disposition"]
    assert "facturas_2026-09-01_2026-09-30.zip" in r.headers["content-disposition"]

    with zipfile.ZipFile(io.BytesIO(r.content)) as zf:
        assert zf.testzip() is None
        nombres = zf.namelist()
        assert "facturas.csv" in nombres
        imagenes = sorted(n for n in nombres if n.endswith(".jpg"))
        assert imagenes == [
            "2026-09-05_papeles-cia-sas_FV-001.jpg",
            "2026-09-05_papeles-cia-sas_FV-001_2.jpg",
            "2026-09-20_papeles-cia-sas_FV-002-A.jpg",
        ]
        filas = list(
            csv.DictReader(io.TextIOWrapper(zf.open("facturas.csv"), encoding="utf-8-sig"))
        )
        assert [f["numero"] for f in filas] == ["FV-001", "FV-002/A"]
        assert set(filas[0]) >= {
            "numero",
            "proveedor",
            "identificacion_fiscal",
            "fecha_emision",
            "fecha_vencimiento",
            "moneda",
            "base_gravable",
            "impuesto",
            "total",
            "total_moneda_base",
            "estado_pago",
            "fecha_pago",
            "imagenes",
        }
        assert (
            filas[0]["imagenes"]
            == "2026-09-05_papeles-cia-sas_FV-001.jpg;2026-09-05_papeles-cia-sas_FV-001_2.jpg"
        )
        assert filas[0]["total"] == "119.0000"
        for nombre in imagenes:
            assert zf.read(nombre)[:2] == b"\xff\xd8"
    assert {dentro_1["id"], dentro_2["id"]}


async def test_rf_fac_007_un_periodo_sin_facturas_devuelve_un_zip_con_csv_vacio(
    cliente: httpx.AsyncClient,
) -> None:
    auth = await _sesion(cliente)
    r = await cliente.get(
        "/api/v1/facturas/exportacion",
        params={"desde": "2000-01-01", "hasta": "2000-01-31"},
        headers=auth,
    )
    assert r.status_code == 200
    with zipfile.ZipFile(io.BytesIO(r.content)) as zf:
        assert zf.namelist() == ["facturas.csv"]


async def test_rf_fac_007_el_rango_es_obligatorio_y_coherente(cliente: httpx.AsyncClient) -> None:
    auth = await _sesion(cliente)
    assert (await cliente.get("/api/v1/facturas/exportacion", headers=auth)).status_code == 422
    r = await cliente.get(
        "/api/v1/facturas/exportacion",
        params={"desde": "2026-09-30", "hasta": "2026-09-01"},
        headers=auth,
    )
    assert r.status_code == 422
    assert r.json()["error"]["code"] == "RANGO_INVALIDO"


async def test_rf_int_008_la_exportacion_tambien_funciona_con_api_key(
    cliente: httpx.AsyncClient,
) -> None:
    auth = await _sesion(cliente)
    clave = (
        await cliente.post("/api/v1/api-keys", json={"nombre": "contador"}, headers=auth)
    ).json()["clave"]
    r = await cliente.get(
        "/api/v1/facturas/exportacion",
        params={"desde": "2026-09-01", "hasta": "2026-09-30"},
        headers={"X-API-Key": clave},
    )
    assert r.status_code == 200
