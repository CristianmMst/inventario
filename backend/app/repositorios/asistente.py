import uuid

import sqlalchemy as sa
from sqlalchemy.ext.asyncio import AsyncSession

from app.modelos.asistente import ChatAsistente


class RepositorioAsistente:
    """Toda consulta lleva `negocio_id` (RN-19), salvo la búsqueda por `chat_id` que hacen las
    herramientas: es la que descubre el negocio, y solo se llega a ella con firma de Retell."""

    def __init__(self, sesion: AsyncSession) -> None:
        self._s = sesion

    def guardar(self, chat: ChatAsistente) -> None:
        self._s.add(chat)

    async def del_negocio(self, negocio_id: uuid.UUID, chat_id: str) -> ChatAsistente | None:
        return (
            await self._s.execute(
                sa.select(ChatAsistente).where(
                    ChatAsistente.negocio_id == negocio_id, ChatAsistente.chat_id == chat_id
                )
            )
        ).scalar_one_or_none()

    async def por_chat_id(self, chat_id: str) -> ChatAsistente | None:
        return await self._s.get(ChatAsistente, chat_id)

    async def marcar_terminado(self, chat_id: str) -> None:
        await self._s.execute(
            sa.update(ChatAsistente)
            .where(ChatAsistente.chat_id == chat_id)
            .values(terminado_en=sa.func.now())
        )
