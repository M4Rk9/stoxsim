#!/usr/bin/env bash
set -Eeuo pipefail

if [[ -n "${STOXSIM_DEPLOY_DIR:-}" ]]; then
  DEPLOY_DIR=$(cd "$STOXSIM_DEPLOY_DIR" && pwd)
else
  DEPLOY_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
fi
ENV_FILE="${DEPLOY_DIR}/.env"
BACKUP_FILE="${1:-}"

if [[ "${CONFIRM_STAGING_RESTORE:-}" != "restore" ]]; then
  echo "Restore replaces the staging database." >&2
  echo "Re-run with CONFIRM_STAGING_RESTORE=restore and a backup file path." >&2
  exit 1
fi

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Missing staging environment file: ${ENV_FILE}" >&2
  exit 1
fi
if [[ -z "$BACKUP_FILE" || ! -f "$BACKUP_FILE" ]]; then
  echo "The requested staging backup does not exist: ${BACKUP_FILE:-<empty>}" >&2
  exit 1
fi
if [[ ! -f "${BACKUP_FILE}.sha256" ]]; then
  echo "The staging backup checksum is missing: ${BACKUP_FILE}.sha256" >&2
  exit 1
fi

COMPOSE=(docker compose --env-file "$ENV_FILE" -f "${DEPLOY_DIR}/compose.yml")
SERVICES_STOPPED=false

restore_error() {
  local status=$?
  local line="$1"
  local command="$2"
  trap - ERR
  echo "Restore command failed at line ${line}: ${command} (exit ${status})." >&2
  exit "$status"
}

restore_exit() {
  local status=$?
  trap - EXIT
  if (( status != 0 )); then
    echo "Staging restore failed with exit ${status}." >&2
    if [[ "$SERVICES_STOPPED" == true ]]; then
      echo "Restarting the original staging services after the failed atomic restore." >&2
      if ! "${COMPOSE[@]}" up --detach --remove-orphans --wait --wait-timeout 600; then
        echo "Staging services also failed to recover; recent service logs follow." >&2
        "${COMPOSE[@]}" ps >&2 || true
        "${COMPOSE[@]}" logs --no-color --tail=200 postgres backend frontend caddy >&2 || true
      fi
    fi
  fi
  exit "$status"
}
trap 'restore_error "$LINENO" "$BASH_COMMAND"' ERR
trap restore_exit EXIT
trap 'exit 129' HUP
trap 'exit 130' INT
trap 'exit 143' TERM

echo "Verifying the staging backup checksum"
(cd "$(dirname "$BACKUP_FILE")" && sha256sum --check "$(basename "${BACKUP_FILE}.sha256")")

echo "Validating the PostgreSQL backup archive"
"${COMPOSE[@]}" config --quiet
"${COMPOSE[@]}" exec -T postgres pg_restore --list < "$BACKUP_FILE" >/dev/null

echo "Stopping public staging services before the atomic database restore"
"${COMPOSE[@]}" stop caddy frontend backend
SERVICES_STOPPED=true

echo "Restoring the staging database in one transaction"
"${COMPOSE[@]}" exec -T postgres \
  sh -euc '
    : "${POSTGRES_USER:?POSTGRES_USER is not configured in the postgres container}"
    : "${POSTGRES_DB:?POSTGRES_DB is not configured in the postgres container}"
    pg_restore \
      --username "$POSTGRES_USER" \
      --dbname "$POSTGRES_DB" \
      --clean \
      --if-exists \
      --single-transaction \
      --exit-on-error \
      --no-owner \
      --no-privileges \
      --verbose
  ' < "$BACKUP_FILE"

echo "Starting and health-checking the restored staging stack"
"${COMPOSE[@]}" up --detach --remove-orphans --wait --wait-timeout 600
SERVICES_STOPPED=false
echo "Staging restore completed from ${BACKUP_FILE}"
