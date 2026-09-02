"""RF-REP-001..008, RN-04, RN-09, RN-16: los siete reportes, paginados y accesibles por API."""

import uuid
from datetime import UTC, datetime, timedelta

import httpx
import pytest
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


async def _producto(cliente: httpx.AsyncClient, auth: dict, nombre: str, **extra: object) -> dict:
    cuerpo = {"nombre": nombre, "unidad_codigo": "unidad"} | extra
    r = await cliente.post("/api/v1/productos", json=cuerpo, headers=auth)
    assert r.status_code == 201, r.text
    return r.json()


async def _mover(
    cliente: httpx.AsyncClient,
    auth: dict,
    producto_id: str,
    tipo: str,
    cantidad: str,
    **extra: object,
) -> dict:
    motivo = {"entrada": "carga_inicial", "salida": "venta", "merma": "rotura"}[tipo]
    cuerpo = {
        "producto_id": producto_id,
        "tipo": tipo,
        "cantidad": cantidad,
        "motivo": motivo,
    } | extra
    if tipo == "merma" and "nota" not in cuerpo:
        cuerpo["nota"] = "se rompió"
    r = await cliente.post("/api/v1/movimientos", json=cuerpo, headers=auth | _clave())
    assert r.status_code == 201, r.text
    return r.json()


async def _reporte(cliente: httpx.AsyncClient, auth: dict, nombre: str, **params: object) -> dict:
    r = await cliente.get(f"/api/v1/reportes/{nombre}", params=params, headers=auth)
    assert r.status_code == 200, r.text
    return r.json()


def _costo(monto: str) -> dict:
    return {"monto": monto, "moneda": "COP"}


# --- RF-REP-001 y RF-REP-007 ---------------------------------------------------------------


async def test_rf_rep_001_bajo_minimo_ordenado_por_deficit_relativo(
    cliente: httpx.AsyncClient,
) -> None:
    auth = await _sesion(cliente)
    leve = await _producto(cliente, auth, "Leve", stock_minimo="10")  # 8/10 -> déficit 20%
    grave = await _producto(cliente, auth, "Grave", stock_minimo="10")  # 2/10 -> déficit 80%
    sano = await _producto(cliente, auth, "Sano", stock_minimo="10")  # 20/10
    sin_minimo = await _producto(cliente, auth, "Sin mínimo")  # 0 pero sin mínimo
    archivado = await _producto(cliente, auth, "Archivado", stock_minimo="10")
    for p, cantidad in [(leve, "8"), (grave, "2"), (sano, "20")]:
        await _mover(cliente, auth, p["id"], "entrada", cantidad)
    await cliente.post(f"/api/v1/productos/{archivado['id']}/archivar", headers=auth)

    datos = (await _reporte(cliente, auth, "bajo-minimo"))["datos"]
    assert [f["producto"]["nombre"] for f in datos] == ["Grave", "Leve"]
    assert datos[0]["stock_actual"] == "2.000" and datos[0]["stock_minimo"] == "10.000"
    assert datos[0]["deficit"] == "8.000" and datos[0]["deficit_relativo"] == "0.8000"
    assert sin_minimo["id"] not in {f["producto"]["id"] for f in datos}


async def test_rf_rep_001_un_producto_en_el_minimo_exacto_cuenta_como_bajo_minimo(
    cliente: httpx.AsyncClient,
) -> None:
    auth = await _sesion(cliente)
    p = await _producto(cliente, auth, "Justo", stock_minimo="5")
    await _mover(cliente, auth, p["id"], "entrada", "5")
    datos = (await _reporte(cliente, auth, "bajo-minimo"))["datos"]
    assert [f["producto"]["id"] for f in datos] == [p["id"]]
    assert datos[0]["deficit"] == "0.000"


async def test_rf_rep_007_agotados_incluye_a_los_sin_minimo_y_excluye_archivados(
    cliente: httpx.AsyncClient,
) -> None:
    auth = await _sesion(cliente)
    sin_minimo = await _producto(cliente, auth, "Sin mínimo")
    con_minimo = await _producto(cliente, auth, "Con mínimo", stock_minimo="3")
    negativo = await _producto(cliente, auth, "Negativo")
    con_stock = await _producto(cliente, auth, "Con stock")
    archivado = await _producto(cliente, auth, "Archivado")
    await _mover(cliente, auth, con_stock["id"], "entrada", "1")
    await _mover(cliente, auth, negativo["id"], "salida", "2", nota="forzado", forzar=True)
    await cliente.post(f"/api/v1/productos/{archivado['id']}/archivar", headers=auth)
    datos = (await _reporte(cliente, auth, "agotados"))["datos"]
    assert {f["producto"]["id"] for f in datos} == {
        sin_minimo["id"],
        con_minimo["id"],
        negativo["id"],
    }
    assert all(f["stock_actual"] in ("0.000", "-2.000") for f in datos)
    # Y el de bajo mínimo solo lista al que tiene mínimo.
    bajo = (await _reporte(cliente, auth, "bajo-minimo"))["datos"]
    assert [f["producto"]["id"] for f in bajo] == [con_minimo["id"]]


async def test_rf_rep_001_el_reporte_usa_el_indice_parcial(sesion: AsyncSession) -> None:
    from app.modelos.identidad import Negocio

    negocio = Negocio(nombre="P", moneda_base="COP", zona_horaria="UTC")
    sesion.add(negocio)
    await sesion.flush()
    await sesion.execute(
        sa.text(
            "insert into productos (negocio_id, sku, nombre, unidad_codigo, stock_minimo)"
            " select :n, 'S-' || g, 'P ' || g, 'unidad', 10 from generate_series(1, 20000) g"
        ),
        {"n": negocio.id},
    )
    await sesion.execute(
        sa.text(
            "insert into stock_productos (producto_id, negocio_id, cantidad, bajo_minimo)"
            " select id, negocio_id, case when random() < 0.01 then 2 else 50 end, false"
            " from productos where negocio_id = :n"
        ),
        {"n": negocio.id},
    )
    await sesion.execute(
        sa.text("update stock_productos set bajo_minimo = cantidad <= 10 where negocio_id = :n"),
        {"n": negocio.id},
    )
    await sesion.execute(sa.text("analyze stock_productos"))
    plan = "\n".join(
        (
            await sesion.execute(
                sa.text(
                    "explain select producto_id from stock_productos"
                    " where negocio_id = :n and bajo_minimo"
                ),
                {"n": negocio.id},
            )
        ).scalars()
    )
    assert "ix_stock_bajo_minimo" in plan, plan


# --- RF-REP-002 ------------------------------------------------------------------------------


async def test_rf_rep_002_sin_movimiento_excluye_a_los_creados_dentro_del_periodo(
    cliente: httpx.AsyncClient, sesion: AsyncSession
) -> None:
    auth = await _sesion(cliente)
    viejo_quieto = await _producto(cliente, auth, "Viejo quieto", costo_actual=_costo("100"))
    viejo_activo = await _producto(cliente, auth, "Viejo activo")
    nuevo = await _producto(cliente, auth, "Nuevo de hace 10 días")
    for p in (viejo_quieto, viejo_activo, nuevo):
        await _mover(cliente, auth, p["id"], "entrada", "3")
    hace_120 = datetime.now(UTC) - timedelta(days=120)
    hace_10 = datetime.now(UTC) - timedelta(days=10)
    # Envejecemos los registros por SQL: los viejos se crearon hace 120 días y su carga también.
    # El trigger de inmutabilidad se apaga solo aquí, para fabricar historia antigua.
    await sesion.execute(sa.text("alter table movimientos disable trigger movimientos_inmutables"))
    for p in (viejo_quieto, viejo_activo):
        await sesion.execute(
            sa.text("update productos set created_at = :t where id = :id"),
            {"t": hace_120, "id": p["id"]},
        )
        await sesion.execute(
            sa.text("update movimientos set ocurrido_en = :t where producto_id = :id"),
            {"t": hace_120, "id": p["id"]},
        )
    await sesion.execute(
        sa.text("update productos set created_at = :t where id = :id"),
        {"t": hace_10, "id": nuevo["id"]},
    )
    await sesion.execute(
        sa.text("update movimientos set ocurrido_en = :t where producto_id = :id"),
        {"t": hace_10, "id": nuevo["id"]},
    )
    await sesion.execute(sa.text("alter table movimientos enable trigger movimientos_inmutables"))
    await sesion.commit()
    await _mover(cliente, auth, viejo_activo["id"], "salida", "1")

    datos = (await _reporte(cliente, auth, "sin-movimiento"))["datos"]  # 90 días por defecto
    assert [f["producto"]["id"] for f in datos] == [viejo_quieto["id"]]
    assert datos[0]["stock_actual"] == "3.000"
    assert datos[0]["valor_a_costo"] == {"monto": "300.0000", "moneda": "COP"}
    assert datos[0]["ultimo_movimiento_en"] is not None

    con_umbral_corto = (await _reporte(cliente, auth, "sin-movimiento", dias=5))["datos"]
    assert {f["producto"]["id"] for f in con_umbral_corto} == {viejo_quieto["id"], nuevo["id"]}
    assert (
        await cliente.get("/api/v1/reportes/sin-movimiento", params={"dias": 0}, headers=auth)
    ).status_code == 422


# --- RF-REP-003 y RN-09 ----------------------------------------------------------------------


async def test_rf_rep_003_valorizacion_a_costo_con_desglose_por_categoria_y_no_valorizables(
    cliente: httpx.AsyncClient,
) -> None:
    auth = await _sesion(cliente)
    c1 = (
        await cliente.post("/api/v1/categorias", json={"nombre": "Cuadernos"}, headers=auth)
    ).json()
    a = await _producto(cliente, auth, "A", categoria_id=c1["id"], costo_actual=_costo("10"))
    b = await _producto(cliente, auth, "B", categoria_id=c1["id"], costo_actual=_costo("2.5"))
    sin_cat = await _producto(cliente, auth, "Sin categoría", costo_actual=_costo("100"))
    sin_costo = await _producto(cliente, auth, "Sin costo")
    sin_stock = await _producto(cliente, auth, "Sin stock", costo_actual=_costo("999"))
    for p, cantidad in [(a, "4"), (b, "10"), (sin_cat, "1"), (sin_costo, "7")]:
        await _mover(cliente, auth, p["id"], "entrada", cantidad)
    assert sin_stock["stock_actual"] == "0.000"

    r = await _reporte(cliente, auth, "valorizacion")
    assert r["total"] == {"monto": "165.0000", "moneda": "COP"}  # 40 + 25 + 100
    por_cat = {c["categoria"]["nombre"] if c["categoria"] else None: c for c in r["por_categoria"]}
    assert por_cat["Cuadernos"]["valor"] == {"monto": "65.0000", "moneda": "COP"}
    assert por_cat["Cuadernos"]["productos"] == 2
    assert por_cat[None]["valor"] == {"monto": "100.0000", "moneda": "COP"}
    assert [f["producto"]["id"] for f in r["no_valorizables"]["datos"]] == [sin_costo["id"]]
    assert r["no_valorizables"]["datos"][0]["stock_actual"] == "7.000"
    assert r["productos_valorizados"] == 3


async def test_rn_09_la_valorizacion_no_admite_fecha_pasada(cliente: httpx.AsyncClient) -> None:
    auth = await _sesion(cliente)
    r = await cliente.get(
        "/api/v1/reportes/valorizacion", params={"fecha": "2025-01-01"}, headers=auth
    )
    assert r.status_code == 422
    assert r.json()["error"]["code"] == "VALORIZACION_SOLO_ACTUAL"


async def test_rn_09_la_valorizacion_usa_el_costo_actual_no_el_historico(
    cliente: httpx.AsyncClient,
) -> None:
    auth = await _sesion(cliente)
    prov = (await cliente.post("/api/v1/proveedores", json={"nombre": "P"}, headers=auth)).json()
    p = await _producto(cliente, auth, "A")
    rc = (
        await cliente.post(
            "/api/v1/recepciones",
            json={
                "proveedor_id": prov["id"],
                "lineas": [
                    {"producto_id": p["id"], "cantidad": "10", "costo_unitario": _costo("5")}
                ],
            },
            headers=auth,
        )
    ).json()
    await cliente.post(
        f"/api/v1/recepciones/{rc['id']}/confirmar", json={}, headers=auth | _clave()
    )
    await cliente.patch(
        f"/api/v1/productos/{p['id']}", json={"costo_actual": _costo("8")}, headers=auth
    )
    r = await _reporte(cliente, auth, "valorizacion")
    assert r["total"]["monto"] == "80.0000"


# --- RF-REP-005 ------------------------------------------------------------------------------


async def test_rf_rep_005_resumen_de_compras_usa_la_tasa_congelada_de_cada_recepcion(
    cliente: httpx.AsyncClient,
) -> None:
    auth = await _sesion(cliente)
    prov = (
        await cliente.post("/api/v1/proveedores", json={"nombre": "Importadora"}, headers=auth)
    ).json()
    c1 = (
        await cliente.post("/api/v1/categorias", json={"nombre": "Importados"}, headers=auth)
    ).json()
    p = await _producto(cliente, auth, "Marcador", categoria_id=c1["id"])
    otro = await _producto(cliente, auth, "Otro")

    async def recibir(
        fecha: str, moneda: str, tasa: str | None, costo: str, producto: dict
    ) -> None:
        cuerpo: dict[str, object] = {
            "proveedor_id": prov["id"],
            "fecha": fecha,
            "moneda": moneda,
            "lineas": [
                {
                    "producto_id": producto["id"],
                    "cantidad": "10",
                    "costo_unitario": {"monto": costo, "moneda": moneda},
                }
            ],
        }
        if tasa:
            cuerpo["tasa_cambio"] = tasa
        rc = await cliente.post("/api/v1/recepciones", json=cuerpo, headers=auth)
        assert rc.status_code == 201, rc.text
        r = await cliente.post(
            f"/api/v1/recepciones/{rc.json()['id']}/confirmar", json={}, headers=auth | _clave()
        )
        assert r.status_code == 200, r.text

    await recibir("2026-09-03", "USD", "4000", "1", p)  # 10 USD × 4000 = 40.000 COP
    await recibir("2026-09-10", "USD", "4200", "1", p)  # misma compra, otra tasa: 42.000 COP
    await recibir("2026-09-15", "COP", None, "500", otro)  # 5.000 COP
    await recibir("2026-10-01", "COP", None, "1", otro)  # fuera del período
    f = await cliente.post(
        "/api/v1/facturas",
        json={
            "proveedor_id": prov["id"],
            "numero": "F-1",
            "fecha_emision": "2026-09-20",
            "moneda": "USD",
            "tasa_cambio": "4100",
            "base_gravable": {"monto": "20", "moneda": "USD"},
            "impuesto": {"monto": "0", "moneda": "USD"},
            "total": {"monto": "20", "moneda": "USD"},
        },
        headers=auth | _clave(),
    )
    assert f.status_code == 201, f.text

    r = await _reporte(cliente, auth, "compras", desde="2026-09-01", hasta="2026-09-30")
    assert r["total_recibido"] == {"monto": "87000.0000", "moneda": "COP"}
    assert r["total_facturado"] == {"monto": "82000.0000", "moneda": "COP"}
    por_prov = {x["proveedor"]["nombre"]: x for x in r["por_proveedor"]}
    assert por_prov["Importadora"]["total_recibido"]["monto"] == "87000.0000"
    assert por_prov["Importadora"]["total_facturado"]["monto"] == "82000.0000"
    por_cat = {
        (x["categoria"]["nombre"] if x["categoria"] else None): x for x in r["por_categoria"]
    }
    assert por_cat["Importados"]["total_recibido"]["monto"] == "82000.0000"
    assert por_cat[None]["total_recibido"]["monto"] == "5000.0000"


# --- RF-REP-006, RN-16 y RN-04 ----------------------------------------------------------------


async def test_rf_rep_006_mermas_separadas_de_las_salidas_y_valorizadas_a_costo(
    cliente: httpx.AsyncClient,
) -> None:
    auth = await _sesion(cliente)
    a = await _producto(cliente, auth, "A", costo_actual=_costo("10"))
    b = await _producto(cliente, auth, "B", costo_actual=_costo("100"))
    for p in (a, b):
        await _mover(cliente, auth, p["id"], "entrada", "20")
    await _mover(cliente, auth, a["id"], "salida", "5")  # no es merma
    await _mover(cliente, auth, a["id"], "merma", "3", motivo="rotura", nota="cayó")
    await _mover(cliente, auth, a["id"], "merma", "1", motivo="vencimiento", nota="caducó")
    anulada = await _mover(cliente, auth, b["id"], "merma", "2", motivo="robo", nota="error")
    await cliente.post(
        f"/api/v1/movimientos/{anulada['id']}/anular",
        json={"nota": "no fue robo"},
        headers=auth | _clave(),
    )
    await _mover(cliente, auth, b["id"], "merma", "1", motivo="robo", nota="sí fue")

    hoy = datetime.now(UTC).date().isoformat()
    r = await _reporte(cliente, auth, "mermas", desde=hoy, hasta=hoy)
    assert r["total_cantidad"] == "5.000"  # 3 + 1 + 1; la anulada no cuenta
    assert r["total_valor"] == {"monto": "140.0000", "moneda": "COP"}  # 4×10 + 1×100
    por_motivo = {x["motivo"]: x for x in r["por_motivo"]}
    assert (
        por_motivo["rotura"]["cantidad"] == "3.000"
        and por_motivo["rotura"]["valor"]["monto"] == "30.0000"
    )
    assert por_motivo["robo"]["cantidad"] == "1.000"
    por_producto = {x["producto"]["nombre"]: x for x in r["por_producto"]["datos"]}
    assert (
        por_producto["A"]["cantidad"] == "4.000"
        and por_producto["B"]["valor"]["monto"] == "100.0000"
    )
    assert "salida" not in {x["motivo"] for x in r["por_motivo"]}


async def test_rn_04_discrepancias_lista_los_movimientos_forzados(
    cliente: httpx.AsyncClient,
) -> None:
    auth = await _sesion(cliente)
    p = await _producto(cliente, auth, "A")
    await _mover(cliente, auth, p["id"], "entrada", "10")
    await _mover(cliente, auth, p["id"], "salida", "2")  # normal
    forzado = await _mover(cliente, auth, p["id"], "salida", "20", nota="confirmado", forzar=True)
    hoy = datetime.now(UTC).date().isoformat()
    r = await _reporte(cliente, auth, "discrepancias", desde=hoy, hasta=hoy)
    assert [x["movimiento_id"] for x in r["datos"]] == [forzado["id"]]
    assert r["datos"][0]["stock_resultante"] == "-12.000" and r["datos"][0]["nota"] == "confirmado"
    assert r["datos"][0]["producto"]["id"] == p["id"]


# --- RF-REP-008 y RF-INT-008 ------------------------------------------------------------------


REPORTES = [
    ("bajo-minimo", {}),
    ("agotados", {}),
    ("sin-movimiento", {}),
    ("valorizacion", {}),
    ("compras", {"desde": "2026-09-01", "hasta": "2026-09-30"}),
    ("mermas", {"desde": "2026-09-01", "hasta": "2026-09-30"}),
    ("discrepancias", {"desde": "2026-09-01", "hasta": "2026-09-30"}),
]


@pytest.mark.parametrize(("nombre", "params"), REPORTES)
async def test_rf_rep_008_los_siete_reportes_responden_igual_con_jwt_y_con_api_key(
    cliente: httpx.AsyncClient, nombre: str, params: dict
) -> None:
    auth = await _sesion(cliente)
    clave = (await cliente.post("/api/v1/api-keys", json={"nombre": "n8n"}, headers=auth)).json()[
        "clave"
    ]
    p = await _producto(cliente, auth, "A", stock_minimo="5", costo_actual=_costo("10"))
    await _mover(cliente, auth, p["id"], "entrada", "1")
    con_jwt = await cliente.get(f"/api/v1/reportes/{nombre}", params=params, headers=auth)
    con_key = await cliente.get(
        f"/api/v1/reportes/{nombre}", params=params, headers={"X-API-Key": clave}
    )
    assert con_jwt.status_code == con_key.status_code == 200, (con_jwt.text, con_key.text)
    assert con_jwt.json() == con_key.json()
    sin = await cliente.get(f"/api/v1/reportes/{nombre}", params=params)
    assert sin.status_code == 401


async def test_rf_rep_008_las_listas_de_los_reportes_se_paginan_por_cursor(
    cliente: httpx.AsyncClient,
) -> None:
    auth = await _sesion(cliente)
    for i in range(5):
        await _producto(cliente, auth, f"Agotado {i}")
    primera = await _reporte(cliente, auth, "agotados", limit=2)
    assert len(primera["datos"]) == 2 and primera["tiene_mas"]
    vistos = [f["producto"]["id"] for f in primera["datos"]]
    pagina = primera
    while pagina["tiene_mas"]:
        pagina = await _reporte(
            cliente, auth, "agotados", limit=2, cursor=pagina["cursor_siguiente"]
        )
        vistos += [f["producto"]["id"] for f in pagina["datos"]]
    assert len(vistos) == len(set(vistos)) == 5


async def test_rf_aut_007_los_reportes_no_cruzan_negocios(cliente: httpx.AsyncClient) -> None:
    a, b = await _sesion(cliente), await _sesion(cliente)
    await _producto(cliente, a, "Agotado de A")
    assert (await _reporte(cliente, b, "agotados"))["datos"] == []
    assert (await _reporte(cliente, b, "valorizacion"))["total"]["monto"] == "0.0000"
