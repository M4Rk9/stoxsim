#!/usr/bin/env bash
set -Eeuo pipefail

DEPLOY_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
ENV_FILE="${DEPLOY_DIR}/.env"
COMPOSE=(docker compose --env-file "$ENV_FILE" -f "${DEPLOY_DIR}/compose.yml")
TARGET_TAG="${1:-}"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Missing ${ENV_FILE}. Copy .env.example to .env and set staging secrets first." >&2
  exit 1
fi

if [[ ! "$TARGET_TAG" =~ ^[0-9a-f]{40}$ ]]; then
  echo "Usage: $0 <40-character-tested-commit-sha>" >&2
  exit 1
fi

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

PREVIOUS_TAG=$(read_env_value STOXSIM_IMAGE_TAG)
if [[ "$PREVIOUS_TAG" =~ ^[0-9a-f]{40}$ && "$PREVIOUS_TAG" != "$TARGET_TAG" ]]; then
  printf '%s\n' "$PREVIOUS_TAG" > "${DEPLOY_DIR}/.previous-image-tag"
fi

write_env_value STOXSIM_IMAGE_TAG "$TARGET_TAG"

echo "Pulling immutable StoxSim images tagged ${TARGET_TAG}"
if ! "${COMPOSE[@]}" pull; then
  write_env_value STOXSIM_IMAGE_TAG "$PREVIOUS_TAG"
  exit 1
fi

echo "Starting private staging services"
if "${COMPOSE[@]}" up --detach --remove-orphans --wait --wait-timeout 240; then
  "${COMPOSE[@]}" ps
  docker image prune --force --filter "until=168h" >/dev/null
  echo "StoxSim staging is healthy on ${TARGET_TAG}"
  exit 0
fi

echo "Deployment failed its internal health checks" >&2
if [[ "$PREVIOUS_TAG" =~ ^[0-9a-f]{40}$ && "$PREVIOUS_TAG" != "$TARGET_TAG" ]]; then
  echo "Rolling back to ${PREVIOUS_TAG}" >&2
  write_env_value STOXSIM_IMAGE_TAG "$PREVIOUS_TAG"
  "${COMPOSE[@]}" pull
  "${COMPOSE[@]}" up --detach --remove-orphans --wait --wait-timeout 240
else
  echo "No previous immutable image tag is available for automatic rollback" >&2
fi
exit 1
