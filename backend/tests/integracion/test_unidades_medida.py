"""RF-CAT-004, RF-INV-009, RN-06: catálogo global de unidades, sembrado por migración."""

import httpx
import sqlalchemy as sa
from sqlalchemy.ext.asyncio import AsyncSession

from tests import fabricas


async def _auth(cliente: httpx.AsyncClient) -> dict[str, str]:
    cuerpo = {
        "email": fabricas.correo_unico(),
        "password": fabricas.CONTRASENA_VALIDA,
        "nombre": "Marta",
        "negocio": {"nombre": "P", "moneda_base": "COP", "zona_horaria": "UTC"},
    }
    r = await cliente.post("/api/v1/auth/registro", json=cuerpo)
    return {"Authorization": f"Bearer {r.json()['token_acceso']}"}


async def test_rf_cat_004_una_base_recien_creada_trae_las_unidades(sesion: AsyncSession) -> None:
    filas = (
        await sesion.execute(
            sa.text("select codigo, tipo, decimales from unidades_medida order by orden")
        )
    ).all()
    por_codigo = {f.codigo: (f.tipo, f.decimales) for f in filas}
    assert por_codigo["unidad"] == ("discreta", 0)
    assert por_codigo["caja"] == ("discreta", 0)
    assert por_codigo["paquete"] == ("discreta", 0)
    assert por_codigo["kg"] == ("continua", 3)
    assert por_codigo["g"] == ("continua", 3)
    assert por_codigo["m"] == ("continua", 3)
    assert por_codigo["l"] == ("continua", 3)


async def test_rf_cat_004_get_unidades_medida_las_devuelve_con_tipo_y_decimales(
    cliente: httpx.AsyncClient,
) -> None:
    respuesta = await cliente.get("/api/v1/unidades-medida", headers=await _auth(cliente))
    assert respuesta.status_code == 200, respuesta.text
    datos = respuesta.json()["datos"]
    unidad = next(u for u in datos if u["codigo"] == "unidad")
    assert unidad == {"codigo": "unidad", "nombre": "Unidad", "tipo": "discreta", "decimales": 0}
    assert len(datos) >= 7
    assert respuesta.json()["tiene_mas"] is False


async def test_rf_cat_004_la_semilla_es_global_y_exige_credencial(
    cliente: httpx.AsyncClient,
) -> None:
    assert (await cliente.get("/api/v1/unidades-medida")).status_code == 401


async def test_rn_06_una_unidad_discreta_no_admite_decimales_en_la_base(
    sesion: AsyncSession,
) -> None:
    import pytest
    from sqlalchemy.exc import IntegrityError

    with pytest.raises(IntegrityError):
        await sesion.execute(
            sa.text(
                "insert into unidades_medida (codigo, nombre, tipo, decimales, orden)"
                " values ('rara', 'Rara', 'discreta', 2, 99)"
            )
        )
    await sesion.rollback()
