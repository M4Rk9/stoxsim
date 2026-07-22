#!/usr/bin/env bash
set -Eeuo pipefail

DEPLOY_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
ENV_FILE="${DEPLOY_DIR}/.env"
BACKUP_DIR="${STOXSIM_BACKUP_DIR:-${DEPLOY_DIR}/backups}"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Missing ${ENV_FILE}." >&2
  exit 1
fi

read_env_value() {
  local key="$1"
  sed -n "s/^${key}=//p" "$ENV_FILE" | tail -n 1
}

POSTGRES_DB=$(read_env_value POSTGRES_DB)
POSTGRES_USER=$(read_env_value POSTGRES_USER)
RETENTION_DAYS=$(read_env_value BACKUP_RETENTION_DAYS)
: "${POSTGRES_DB:?Set POSTGRES_DB in .env}"
: "${POSTGRES_USER:?Set POSTGRES_USER in .env}"
RETENTION_DAYS="${RETENTION_DAYS:-14}"
if [[ ! "$RETENTION_DAYS" =~ ^[0-9]+$ ]]; then
  echo "BACKUP_RETENTION_DAYS must be a non-negative integer." >&2
  exit 1
fi

mkdir -p "$BACKUP_DIR"
BACKUP_DIR=$(cd "$BACKUP_DIR" && pwd)
if [[ "$BACKUP_DIR" == "/" ]]; then
  echo "Refusing to use the filesystem root as the backup directory." >&2
  exit 1
fi

umask 077
timestamp=$(date -u +%Y%m%dT%H%M%SZ)
backup_file="${BACKUP_DIR}/stoxsim-${timestamp}.dump"
partial_file="${backup_file}.partial"
COMPOSE=(docker compose --env-file "$ENV_FILE" -f "${DEPLOY_DIR}/compose.yml")
trap 'rm -f "$partial_file"' EXIT

echo "Writing PostgreSQL backup to ${backup_file}"
"${COMPOSE[@]}" exec -T postgres \
  pg_dump --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
  --format=custom --compress=9 --no-owner > "$partial_file"
mv "$partial_file" "$backup_file"
(
  cd "$BACKUP_DIR"
  sha256sum "$(basename "$backup_file")" > "$(basename "${backup_file}.sha256")"
)

find "$BACKUP_DIR" -maxdepth 1 -type f \
  \( -name 'stoxsim-*.dump' -o -name 'stoxsim-*.dump.sha256' \) \
  -mtime "+${RETENTION_DAYS}" -delete

echo "Backup completed and verified with SHA-256"
