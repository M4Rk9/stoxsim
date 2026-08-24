#!/usr/bin/env bash
set -Eeuo pipefail

DEPLOY_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
ENV_FILE="${DEPLOY_DIR}/.env"
COMPOSE=(docker compose --env-file "$ENV_FILE" -f "${DEPLOY_DIR}/compose.yml")
PREVIOUS_BUNDLE="${DEPLOY_DIR}/.previous-deployment-bundle.tgz"
TARGET_TAG="${1:-}"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Missing ${ENV_FILE}. Copy .env.example to .env and set production secrets first." >&2
  exit 1
fi
if [[ ! "$TARGET_TAG" =~ ^[0-9a-f]{40}$ ]]; then
  echo "Usage: $0 <40-character-tested-commit-sha>" >&2
  exit 1
fi

if ! command -v python3 >/dev/null 2>&1; then
  echo "Python 3 is required to render protected monitoring configuration." >&2
  exit 1
fi
python3 "${DEPLOY_DIR}/render-monitoring-config.py"

read_env_value() {
  local key="$1"
  sed -n "s/^${key}=//p" "$ENV_FILE" | tail -n 1
}

write_env_value() {
  local key="$1"
  local value="$2"
  local temporary
  temporary=$(mktemp "${ENV_FILE}.XXXXXX")
  awk -v key="$key" -v value="$value" '
    BEGIN { replaced = 0 }
    $0 ~ "^" key "=" {
      if (!replaced) print key "=" value
      replaced = 1
      next
    }
    { print }
    END { if (!replaced) print key "=" value }
  ' "$ENV_FILE" > "$temporary"
  chmod --reference="$ENV_FILE" "$temporary"
  mv "$temporary" "$ENV_FILE"
}

restore_previous_bundle() {
  if [[ ! -f "$PREVIOUS_BUNDLE" ]]; then
    echo "No previous production bundle is available for rollback." >&2
    return 1
  fi
  echo "Restoring the previous production bundle"
  tar -xzf "$PREVIOUS_BUNDLE" -C "$DEPLOY_DIR"
  python3 "${DEPLOY_DIR}/render-monitoring-config.py"
}

PREVIOUS_TAG=$(read_env_value STOXSIM_IMAGE_TAG)
BACKUP_BEFORE_DEPLOY=$(read_env_value BACKUP_BEFORE_DEPLOY)
BACKUP_BEFORE_DEPLOY="${BACKUP_BEFORE_DEPLOY:-true}"
if [[ ! "$BACKUP_BEFORE_DEPLOY" =~ ^(true|false)$ ]]; then
  echo "BACKUP_BEFORE_DEPLOY must be true or false." >&2
  exit 1
fi

if "${COMPOSE[@]}" ps --status running --services 2>/dev/null | grep -qx postgres; then
  if [[ "$BACKUP_BEFORE_DEPLOY" == "true" ]]; then
    echo "Creating verified pre-deployment database backup"
    "${DEPLOY_DIR}/backup.sh"
  else
    echo "WARNING: pre-deployment backup is disabled." >&2
  fi
fi

if [[ "$PREVIOUS_TAG" =~ ^[0-9a-f]{40}$ && "$PREVIOUS_TAG" != "$TARGET_TAG" ]]; then
  printf '%s\n' "$PREVIOUS_TAG" > "${DEPLOY_DIR}/.previous-image-tag"
fi
write_env_value STOXSIM_IMAGE_TAG "$TARGET_TAG"

echo "Pulling immutable StoxSim production images tagged ${TARGET_TAG}"
if ! "${COMPOSE[@]}" pull; then
  write_env_value STOXSIM_IMAGE_TAG "$PREVIOUS_TAG"
  restore_previous_bundle || true
  exit 1
fi

echo "Validating the production edge configuration"
if ! "${COMPOSE[@]}" run --rm --no-deps caddy \
  caddy validate --config /etc/caddy/Caddyfile --adapter caddyfile; then
  write_env_value STOXSIM_IMAGE_TAG "$PREVIOUS_TAG"
  restore_previous_bundle || true
  exit 1
fi

echo "Starting production services"
if "${COMPOSE[@]}" up --detach --remove-orphans --wait --wait-timeout 600 \
  && "${COMPOSE[@]}" up --detach --no-deps --force-recreate caddy \
  && "${COMPOSE[@]}" up --detach --no-deps --force-recreate alertmanager blackbox-exporter prometheus grafana \
  && "${COMPOSE[@]}" up --detach --remove-orphans --wait --wait-timeout 600; then
  "${COMPOSE[@]}" ps
  docker image prune --force --filter "until=336h" >/dev/null
  echo "StoxSim production is healthy on ${TARGET_TAG}"
  exit 0
fi

echo "Production deployment failed internal health checks" >&2
if [[ "$PREVIOUS_TAG" =~ ^[0-9a-f]{40}$ && "$PREVIOUS_TAG" != "$TARGET_TAG" ]]; then
  echo "Rolling back to ${PREVIOUS_TAG}" >&2
  restore_previous_bundle || true
  write_env_value STOXSIM_IMAGE_TAG "$PREVIOUS_TAG"
  "${COMPOSE[@]}" pull
  "${COMPOSE[@]}" up --detach --remove-orphans --wait --wait-timeout 600
  "${COMPOSE[@]}" up --detach --no-deps --force-recreate caddy alertmanager blackbox-exporter prometheus grafana
else
  echo "No previous immutable image tag is available for automatic rollback" >&2
fi
exit 1
