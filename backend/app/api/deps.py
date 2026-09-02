"""Dependencias de FastAPI compartidas por todos los routers."""

from typing import Annotated

from fastapi import Query

from app.infra.paginacion import LIMITE_MAXIMO, LIMITE_POR_DEFECTO, ParametrosPagina


def paginacion(
    cursor: Annotated[str | None, Query(description="Cursor opaco de la página anterior")] = None,
    limit: Annotated[int, Query(ge=1, le=LIMITE_MAXIMO)] = LIMITE_POR_DEFECTO,
) -> ParametrosPagina:
    return ParametrosPagina(cursor=cursor, limit=limit)
