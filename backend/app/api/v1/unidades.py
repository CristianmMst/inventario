from typing import Annotated

from fastapi import APIRouter, Depends

from app.api.deps import Contexto, SesionDb, paginacion
from app.esquemas.catalogo import UnidadMedidaSalida
from app.infra.paginacion import Pagina, ParametrosPagina, decodificar_cursor, paginar
from app.repositorios.catalogo import RepositorioUnidades

router = APIRouter(prefix="/unidades-medida", tags=["catalogo"])


@router.get("", response_model=Pagina[UnidadMedidaSalida])
async def listar(
    sesion: SesionDb,
    _: Contexto,
    pagina: Annotated[ParametrosPagina, Depends(paginacion)],
) -> Pagina[UnidadMedidaSalida]:
    """Catálogo de unidades: discretas exigen enteros, continuas admiten decimales (RF-CAT-004)."""
    despues = int(decodificar_cursor(pagina.cursor)["orden"]) if pagina.cursor else None
    filas = await RepositorioUnidades(sesion).listar(pagina.limit + 1, despues)
    salida = [
        UnidadMedidaSalida(codigo=f.codigo, nombre=f.nombre, tipo=f.tipo, decimales=f.decimales)
        for f in filas
    ]
    ordenes = {f.codigo: f.orden for f in filas}
    return paginar(salida, pagina.limit, clave_de=lambda u: {"orden": ordenes[u.codigo]})
