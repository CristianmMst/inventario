from datetime import date
from typing import Annotated

from fastapi import APIRouter, Depends, Query

from app.api.deps import Contexto, SesionDb, paginacion
from app.dominio import errores as err
from app.esquemas.reportes import (
    FilaAgotado,
    FilaBajoMinimo,
    FilaDiscrepancia,
    FilaSinMovimiento,
    Lista,
    ResumenCompras,
    ResumenMermas,
    Valorizacion,
)
from app.infra.paginacion import ParametrosPagina
from app.servicios import reportes as servicio

router = APIRouter(prefix="/reportes", tags=["reportes"])

Pagina_ = Annotated[ParametrosPagina, Depends(paginacion)]
Desde = Annotated[date, Query(description="Inclusive")]
Hasta = Annotated[date, Query(description="Inclusive")]


@router.get("/bajo-minimo", response_model=Lista[FilaBajoMinimo])
async def bajo_minimo(
    sesion: SesionDb, contexto: Contexto, pagina: Pagina_
) -> Lista[FilaBajoMinimo]:
    """Productos activos con stock ≤ mínimo, ordenados por déficit relativo. Los productos sin
    mínimo no aparecen (RF-REP-001)."""
    return await servicio.bajo_minimo(sesion, contexto.negocio_id, pagina)


@router.get("/agotados", response_model=Lista[FilaAgotado])
async def agotados(sesion: SesionDb, contexto: Contexto, pagina: Pagina_) -> Lista[FilaAgotado]:
    """Productos activos con stock ≤ 0, tengan o no mínimo (RF-REP-007)."""
    return await servicio.agotados(sesion, contexto.negocio_id, pagina)


@router.get("/sin-movimiento", response_model=Lista[FilaSinMovimiento])
async def sin_movimiento(
    sesion: SesionDb,
    contexto: Contexto,
    pagina: Pagina_,
    dias: Annotated[int, Query(ge=1, le=3650, description="Umbral en días; 90 por defecto")] = 90,
) -> Lista[FilaSinMovimiento]:
    """Activos sin movimientos en los últimos N días, excluyendo los creados en ese período, con
    su stock y su valor a costo (RF-REP-002)."""
    return await servicio.sin_movimiento(sesion, contexto.negocio_id, pagina, dias=dias)


@router.get("/valorizacion", response_model=Valorizacion)
async def valorizacion(
    sesion: SesionDb,
    contexto: Contexto,
    pagina: Pagina_,
    fecha: Annotated[
        str | None, Query(description="No admitido: la valorización es siempre actual (RN-09)")
    ] = None,
) -> Valorizacion:
    """Σ stock × costo actual en moneda base con desglose por categoría; los productos con stock
    y sin costo van aparte como no valorizables y no cuentan como cero (RF-REP-003)."""
    if fecha is not None:
        raise err.ValidacionInvalida(
            "VALORIZACION_SOLO_ACTUAL",
            "La valorización usa el costo actual y no se puede calcular a una fecha pasada "
            "(RN-09).",
            {"parametro": "fecha"},
        )
    return await servicio.valorizacion(sesion, contexto.negocio_id, pagina)


@router.get("/compras", response_model=ResumenCompras)
async def compras(
    sesion: SesionDb, contexto: Contexto, desde: Desde, hasta: Hasta
) -> ResumenCompras:
    """Total recibido y facturado del período en moneda base, con las tasas congeladas en cada
    documento, por proveedor y por categoría (RF-REP-005)."""
    return await servicio.compras(sesion, contexto.negocio_id, desde, hasta)


@router.get("/mermas", response_model=ResumenMermas)
async def mermas(
    sesion: SesionDb, contexto: Contexto, pagina: Pagina_, desde: Desde, hasta: Hasta
) -> ResumenMermas:
    """Cantidad y valor a costo de las mermas del período, por motivo y por producto (RF-REP-006).
    Separadas de las salidas (RN-16); las anuladas no cuentan."""
    return await servicio.mermas(sesion, contexto.negocio_id, pagina, desde, hasta)


@router.get("/discrepancias", response_model=Lista[FilaDiscrepancia])
async def discrepancias(
    sesion: SesionDb, contexto: Contexto, pagina: Pagina_, desde: Desde, hasta: Hasta
) -> Lista[FilaDiscrepancia]:
    """Movimientos forzados del período: discrepancias a resolver, no un estado normal (RN-04)."""
    return await servicio.discrepancias(sesion, contexto.negocio_id, pagina, desde, hasta)
