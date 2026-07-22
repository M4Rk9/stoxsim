#!/usr/bin/env bash
set -Eeuo pipefail

DEPLOY_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
ENV_FILE="${DEPLOY_DIR}/.env"
BACKUP_FILE="${1:-}"

if [[ "${CONFIRM_STAGING_RESTORE:-}" != "restore" ]]; then
  echo "Restore replaces the staging database." >&2
  echo "Re-run with CONFIRM_STAGING_RESTORE=restore and a backup file path." >&2
  exit 1
fi

if [[ ! -f "$ENV_FILE" || ! -f "$BACKUP_FILE" ]]; then
  echo "A configured .env and an existing backup file are required." >&2
  exit 1
fi

if [[ -f "${BACKUP_FILE}.sha256" ]]; then
  (cd "$(dirname "$BACKUP_FILE")" && sha256sum --check "$(basename "${BACKUP_FILE}.sha256")")
fi

read_env_value() {
  local key="$1"
  sed -n "s/^${key}=//p" "$ENV_FILE" | tail -n 1
}

POSTGRES_DB=$(read_env_value POSTGRES_DB)
POSTGRES_USER=$(read_env_value POSTGRES_USER)
: "${POSTGRES_DB:?Set POSTGRES_DB in .env}"
: "${POSTGRES_USER:?Set POSTGRES_USER in .env}"

COMPOSE=(docker compose --env-file "$ENV_FILE" -f "${DEPLOY_DIR}/compose.yml")
echo "Stopping public staging services before database restore"
"${COMPOSE[@]}" stop caddy frontend backend

"${COMPOSE[@]}" exec -T postgres \
  dropdb --username "$POSTGRES_USER" --if-exists --force "$POSTGRES_DB"
"${COMPOSE[@]}" exec -T postgres \
  createdb --username "$POSTGRES_USER" "$POSTGRES_DB"
"${COMPOSE[@]}" exec -T postgres \
  pg_restore --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
  --exit-on-error --no-owner < "$BACKUP_FILE"

"${COMPOSE[@]}" up --detach --remove-orphans --wait --wait-timeout 240
echo "Staging restore completed from ${BACKUP_FILE}"
