#!/bin/sh
# Arranque de la imagen autónoma (Dockerfile.unico): PostgreSQL local + migraciones + API.
set -eu

PGDATA=/datos/postgres
SOCKETS=/tmp
SECRETOS=/datos/secretos.env

# Secretos: los que vengan por variable de entorno mandan; si no, se generan una vez y se
# guardan en el volumen para que los tokens sigan valiendo tras reiniciar.
if [ ! -f "$SECRETOS" ]; then
    umask 077
    python -c "import secrets; print('JWT_SECRETO_GENERADO=' + secrets.token_urlsafe(48)); print('IMAGENES_SECRETO_GENERADO=' + secrets.token_urlsafe(48))" > "$SECRETOS"
fi
. "$SECRETOS"
export JWT_SECRETO="${JWT_SECRETO:-$JWT_SECRETO_GENERADO}"
export IMAGENES_SECRETO="${IMAGENES_SECRETO:-$IMAGENES_SECRETO_GENERADO}"

# Base de datos interna: solo escucha en 127.0.0.1, así que no se expone fuera del contenedor.
if [ ! -s "$PGDATA/PG_VERSION" ]; then
    echo "Inicializando PostgreSQL en $PGDATA"
    initdb -D "$PGDATA" -U inventario --auth=trust --encoding=UTF8 --locale=C.UTF-8 >/dev/null
fi
pg_ctl -D "$PGDATA" -w -l /datos/postgres.log \
    -o "-c listen_addresses=127.0.0.1 -c unix_socket_directories=$SOCKETS" start
psql -h 127.0.0.1 -U inventario -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname='inventario'" \
    | grep -q 1 || createdb -h 127.0.0.1 -U inventario inventario

export DATABASE_URL="${DATABASE_URL:-postgresql+asyncpg://inventario@127.0.0.1:5432/inventario}"
mkdir -p "$IMAGENES_DIR"

detener() {
    [ -n "${API:-}" ] && kill -TERM "$API" 2>/dev/null && wait "$API" || true
    pg_ctl -D "$PGDATA" -m fast stop
    exit 0
}
trap detener TERM INT

alembic upgrade head
uvicorn app.main:app --host 0.0.0.0 --port "$PORT" --workers "$WEB_CONCURRENCY" \
    --proxy-headers --forwarded-allow-ips='*' &
API=$!
wait "$API"
pg_ctl -D "$PGDATA" -m fast stop
