import httpx

from app.main import app


async def test_salud_responde_200_con_estado_ok() -> None:
    transporte = httpx.ASGITransport(app=app)
    async with httpx.AsyncClient(transport=transporte, base_url="http://test") as cliente:
        respuesta = await cliente.get("/api/v1/salud")
    assert respuesta.status_code == 200
    assert respuesta.json() == {"estado": "ok"}
