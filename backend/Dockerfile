# Imagen de producción. En desarrollo, docker-compose.yml monta el código y añade --reload.
FROM python:3.12-slim AS construccion
COPY --from=ghcr.io/astral-sh/uv:0.11 /uv /bin/
WORKDIR /srv
ENV UV_COMPILE_BYTECODE=1 UV_LINK_MODE=copy UV_PROJECT_ENVIRONMENT=/opt/venv UV_PYTHON_DOWNLOADS=never
COPY pyproject.toml uv.lock ./
RUN uv sync --frozen --no-dev --no-install-project
COPY . .
RUN uv sync --frozen --no-dev

FROM python:3.12-slim
ENV PATH="/opt/venv/bin:$PATH" PYTHONUNBUFFERED=1 \
    ENTORNO=produccion IMAGENES_DIR=/datos/imagenes WEB_CONCURRENCY=2
RUN useradd --system --uid 10001 --home /srv app \
    && mkdir -p /datos/imagenes && chown -R app /datos
WORKDIR /srv
COPY --from=construccion /opt/venv /opt/venv
COPY --from=construccion --chown=app /srv /srv
USER app
EXPOSE 8000
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
    CMD python -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8000/api/v1/salud', timeout=4)"
# Migra antes de servir; si la migración falla, el contenedor no arranca.
CMD ["sh", "-c", "alembic upgrade head && exec uvicorn app.main:app --host 0.0.0.0 --port 8000 --workers ${WEB_CONCURRENCY} --proxy-headers --forwarded-allow-ips='*'"]
