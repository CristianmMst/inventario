from fastapi import APIRouter, FastAPI

app = FastAPI(title="Inventario API", version="1.0.0")

api_v1 = APIRouter(prefix="/api/v1")


@api_v1.get("/salud", tags=["sistema"])
async def salud() -> dict[str, str]:
    return {"estado": "ok"}


app.include_router(api_v1)
