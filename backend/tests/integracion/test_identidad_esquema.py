"""RF-AUT-001, RF-AUT-004, RN-19: esquema de usuarios, negocios y membresías."""

import uuid

import pytest
import sqlalchemy as sa
from sqlalchemy.exc import IntegrityError
from sqlalchemy.ext.asyncio import AsyncSession

from app.modelos.identidad import Membresia, Negocio, Usuario


async def test_rf_aut_001_el_correo_es_unico_sin_distinguir_mayusculas(
    sesion: AsyncSession,
) -> None:
    sesion.add(Usuario(email="Marta@Papeleria.co", password_hash="x", nombre="Marta"))
    await sesion.commit()
    sesion.add(Usuario(email="marta@papeleria.co", password_hash="x", nombre="Otra"))
    with pytest.raises(IntegrityError):
        await sesion.commit()


async def test_rf_aut_004_el_negocio_guarda_moneda_base_y_zona_horaria(
    sesion: AsyncSession,
) -> None:
    negocio = Negocio(nombre="Papelería Marta", moneda_base="COP", zona_horaria="America/Bogota")
    sesion.add(negocio)
    await sesion.commit()
    fila = (
        await sesion.execute(
            sa.text("select moneda_base, zona_horaria, created_at from negocios where id = :id"),
            {"id": negocio.id},
        )
    ).one()
    assert fila.moneda_base == "COP"
    assert fila.zona_horaria == "America/Bogota"
    assert fila.created_at.tzinfo is not None


async def test_rn_19_membresias_existe_como_tabla_puente_con_una_fila_por_par(
    sesion: AsyncSession,
) -> None:
    usuario = Usuario(email="m@p.co", password_hash="x", nombre="Marta")
    negocio = Negocio(nombre="Papelería", moneda_base="COP", zona_horaria="America/Bogota")
    sesion.add_all([usuario, negocio])
    await sesion.flush()
    sesion.add(Membresia(usuario_id=usuario.id, negocio_id=negocio.id, rol="dueno"))
    await sesion.commit()
    sesion.add(Membresia(usuario_id=usuario.id, negocio_id=negocio.id, rol="dueno"))
    with pytest.raises(IntegrityError):
        await sesion.commit()


async def test_rn_19_una_membresia_exige_usuario_y_negocio_existentes(
    sesion: AsyncSession,
) -> None:
    sesion.add(Membresia(usuario_id=uuid.uuid4(), negocio_id=uuid.uuid4(), rol="dueno"))
    with pytest.raises(IntegrityError):
        await sesion.commit()
