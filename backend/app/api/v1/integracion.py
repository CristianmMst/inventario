from fastapi import APIRouter

from app.api.deps import SesionDb
from app.esquemas.reportes import StockBajoPorNegocio
from app.servicios import reportes as servicio

router = APIRouter(prefix="/integracion", tags=["integracion"])


@router.get("/stock-bajo", response_model=StockBajoPorNegocio)
async def stock_bajo(sesion: SesionDb) -> StockBajoPorNegocio:
    """Todos los negocios que tienen productos con stock ≤ mínimo, con el nombre y el email del
    dueño, para enviarles un aviso por correo desde n8n.

    **Pública, sin credencial**, por decisión del proyecto: cualquiera que conozca la URL ve los
    emails de los dueños y sus productos bajo el mínimo. Negocios ordenados por nombre;
    productos por déficit relativo, el más urgente primero. Los negocios sin nada bajo no
    aparecen."""
    return await servicio.bajo_minimo_de_todos(sesion)
