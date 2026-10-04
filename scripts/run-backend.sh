#!/bin/sh
set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)

cd "$ROOT_DIR"
# Load needed variables from .env if they aren't already exported
#Load needed variables from .env if they aren't already exported
for name in DB_PASSWORD DB_MIGRATION_PASSWORD JWT_SECRET_BASE64; do
    current=$(printenv "$name" || true)
    if [ -z "$current" ] && [ -f .env ]; then
        value=$(sed -n "s/^[[:space:]]*$name[[:space:]]*=[[:space:]]*//p" .env | tail -n 1)
        value=${value%\"}; value=${value#\"}
        value=${value%\'}; value=${value#\'}
        [ -n "$value" ] && export "$name=$value"
    fi
done
has_nonempty_setting() {
    grep -Eq "^[[:space:]]*$1[[:space:]]*=[[:space:]]*[^[:space:]#]" .env 2>/dev/null
}

if [ -z "${DB_PASSWORD:-}" ] && ! has_nonempty_setting DB_PASSWORD; then
    printf '%s\n' 'Set DB_PASSWORD in the root .env or export it before running this script.' >&2
    exit 1
fi

if [ -z "${DB_MIGRATION_PASSWORD:-}" ] && ! has_nonempty_setting DB_MIGRATION_PASSWORD; then
    printf '%s\n' 'Set DB_MIGRATION_PASSWORD in the root .env or export it before running this script.' >&2
    exit 1
fi

export DB_URL=jdbc:postgresql://localhost:5432/devpulse
export DB_USERNAME=devpulse_app
export DB_MIGRATION_URL=jdbc:postgresql://localhost:5432/devpulse
export DB_MIGRATION_USERNAME=devpulse_migrator

if ! docker inspect --format '{{.State.Running}}' devpulse-postgres-1 2>/dev/null | grep -qx true; then
    docker compose up -d postgres
fi

waited=0
while [ "$waited" -lt 60 ]; do
    health=$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' devpulse-postgres-1 2>/dev/null || true)
    if [ "$health" = healthy ]; then
        break
    fi
    if [ "$health" = unhealthy ]; then
        printf '%s\n' 'PostgreSQL container is unhealthy; inspect it with docker compose logs postgres.' >&2
        exit 1
    fi
    sleep 2
    waited=$((waited + 2))
done

if [ "$health" != healthy ]; then
    printf '%s\n' 'Timed out waiting for PostgreSQL to become healthy.' >&2
    exit 1
fi

exec ./backend/mvnw -f backend/pom.xml spring-boot:run \
    -Dspring-boot.run.arguments=--server.port=8082
