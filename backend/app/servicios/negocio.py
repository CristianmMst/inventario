"""RF-AUT-004 / RN-10: datos del negocio; la moneda base se congela con el primer documento
valorizado."""

import uuid

from sqlalchemy.ext.asyncio import AsyncSession

from app.dominio import errores as err
from app.dominio.tipos import Moneda
from app.esquemas.negocio import NegocioEdicion
from app.modelos.identidad import Negocio
from app.repositorios.compras import RepositorioRecepciones
from app.repositorios.identidad import RepositorioIdentidad


async def editar(sesion: AsyncSession, negocio_id: uuid.UUID, datos: NegocioEdicion) -> Negocio:
    async with sesion.begin():
        negocio = await RepositorioIdentidad(sesion).negocio_por_id(negocio_id)
        if datos.nombre is not None:
            negocio.nombre = datos.nombre
        if datos.zona_horaria is not None:
            negocio.zona_horaria = datos.zona_horaria
        if datos.moneda_base is not None and datos.moneda_base != negocio.moneda_base:
            Moneda(datos.moneda_base)
            if await RepositorioRecepciones(sesion).hay_confirmadas(negocio_id):
                raise err.Conflicto(
                    "MONEDA_BASE_INMUTABLE",
                    "Ya hay compras valorizadas en la moneda actual; la moneda base no se puede "
                    "cambiar.",
                    {"moneda_base": negocio.moneda_base},
                )
            negocio.moneda_base = datos.moneda_base
        await sesion.flush()
        await sesion.refresh(negocio)
        return negocio
