from collections.abc import AsyncIterator

from sqlalchemy.ext.asyncio import (
    AsyncEngine,
    AsyncSession,
    async_sessionmaker,
    create_async_engine,
)

from app.config import obtener_ajustes

_motor: AsyncEngine | None = None
_sesiones: async_sessionmaker[AsyncSession] | None = None


def motor() -> AsyncEngine:
    global _motor, _sesiones
    if _motor is None:
        _motor = create_async_engine(obtener_ajustes().database_url, pool_pre_ping=True)
        _sesiones = async_sessionmaker(_motor, expire_on_commit=False)
    return _motor


def fabrica_sesiones() -> async_sessionmaker[AsyncSession]:
    motor()
    assert _sesiones is not None
    return _sesiones


async def sesion() -> AsyncIterator[AsyncSession]:
    """Dependencia de FastAPI: una sesión por petición. La transacción la abre el servicio."""
    async with fabrica_sesiones()() as s:
        yield s


async def reiniciar_motor() -> None:
    """Para pruebas: descarta el motor para que tome una URL nueva."""
    global _motor, _sesiones
    if _motor is not None:
        await _motor.dispose()
    _motor = None
    _sesiones = None
