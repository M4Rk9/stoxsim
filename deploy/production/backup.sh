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
BACKUP_S3_URI=$(read_env_value BACKUP_S3_URI)
REQUIRE_OFFSITE_BACKUP=$(read_env_value REQUIRE_OFFSITE_BACKUP)
: "${POSTGRES_DB:?Set POSTGRES_DB in .env}"
: "${POSTGRES_USER:?Set POSTGRES_USER in .env}"
RETENTION_DAYS="${RETENTION_DAYS:-7}"
REQUIRE_OFFSITE_BACKUP="${REQUIRE_OFFSITE_BACKUP:-true}"

if [[ ! "$RETENTION_DAYS" =~ ^[0-9]+$ ]]; then
  echo "BACKUP_RETENTION_DAYS must be a non-negative integer." >&2
  exit 1
fi
if [[ ! "$REQUIRE_OFFSITE_BACKUP" =~ ^(true|false)$ ]]; then
  echo "REQUIRE_OFFSITE_BACKUP must be true or false." >&2
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
backup_file="${BACKUP_DIR}/stoxsim-production-${timestamp}.dump"
partial_file="${backup_file}.partial"
checksum_file="${backup_file}.sha256"
COMPOSE=(docker compose --env-file "$ENV_FILE" -f "${DEPLOY_DIR}/compose.yml")
trap 'rm -f "$partial_file"' EXIT

echo "Writing PostgreSQL backup to ${backup_file}"
"${COMPOSE[@]}" exec -T postgres   pg_dump --username "$POSTGRES_USER" --dbname "$POSTGRES_DB"   --format=custom --compress=9 --no-owner > "$partial_file"
mv "$partial_file" "$backup_file"
(
  cd "$BACKUP_DIR"
  sha256sum "$(basename "$backup_file")" > "$(basename "$checksum_file")"
)
(
  cd "$BACKUP_DIR"
  sha256sum --check "$(basename "$checksum_file")"
)

if [[ -n "$BACKUP_S3_URI" ]]; then
  if ! command -v aws >/dev/null 2>&1; then
    echo "BACKUP_S3_URI is set but the AWS CLI is unavailable." >&2
    exit 1
  fi
  destination="${BACKUP_S3_URI%/}/$(basename "$backup_file")"
  echo "Uploading encrypted/off-host copy to ${destination}"
  aws s3 cp --only-show-errors "$backup_file" "$destination"
  aws s3 cp --only-show-errors "$checksum_file" "${destination}.sha256"
elif [[ "$REQUIRE_OFFSITE_BACKUP" == "true" ]]; then
  echo "Production backup requires BACKUP_S3_URI." >&2
  exit 1
else
  echo "WARNING: backup exists only on this host." >&2
fi

find "$BACKUP_DIR" -maxdepth 1 -type f   $( -name 'stoxsim-production-*.dump' -o -name 'stoxsim-production-*.dump.sha256' $)   -mtime "+${RETENTION_DAYS}" -delete

echo "Production backup completed and checksum verified"
