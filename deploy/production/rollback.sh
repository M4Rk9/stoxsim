#!/usr/bin/env bash
set -Eeuo pipefail

DEPLOY_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
ENV_FILE="${DEPLOY_DIR}/.env"
PREVIOUS_FILE="${DEPLOY_DIR}/.previous-image-tag"
PREVIOUS_BUNDLE="${DEPLOY_DIR}/.previous-deployment-bundle.tgz"

if [[ ! -f "$ENV_FILE" || ! -f "$PREVIOUS_FILE" || ! -f "$PREVIOUS_BUNDLE" ]]; then
  echo "A configured .env, previous image tag and previous deployment bundle are required to roll back." >&2
  exit 1
fi

TARGET_TAG=$(tr -d '[:space:]' < "$PREVIOUS_FILE")
if [[ ! "$TARGET_TAG" =~ ^[0-9a-f]{40}$ ]]; then
  echo "The previous image tag is not a valid immutable commit SHA." >&2
  exit 1
fi

CURRENT_TAG=$(sed -n 's/^STOXSIM_IMAGE_TAG=//p' "$ENV_FILE" | tail -n 1)
if [[ ! "$CURRENT_TAG" =~ ^[0-9a-f]{40}$ ]]; then
  echo "The current image tag is not a valid immutable commit SHA." >&2
  exit 1
fi

write_env_value() {
  local value="$1"
  local temporary
  temporary=$(mktemp "${ENV_FILE}.XXXXXX")
  awk -v value="$value" '
    /^STOXSIM_IMAGE_TAG=/ { print "STOXSIM_IMAGE_TAG=" value; next }
    { print }
  ' "$ENV_FILE" > "$temporary"
  chmod --reference="$ENV_FILE" "$temporary"
  mv "$temporary" "$ENV_FILE"
}

capture_bundle() {
  local destination="$1"
  tar \
    --exclude=.env \
    --exclude=backups \
    --exclude=.previous-image-tag \
    --exclude=.previous-deployment-bundle.tgz \
    --exclude=monitoring/alertmanager.yml \
    --exclude=monitoring/metrics-scrape-token \
    -C "$DEPLOY_DIR" -czf "$destination" .
}

CURRENT_BUNDLE=$(mktemp /tmp/stoxsim-current-bundle.XXXXXX.tgz)
trap 'rm -f "$CURRENT_BUNDLE"' EXIT
capture_bundle "$CURRENT_BUNDLE"

echo "Restoring production bundle and images from ${TARGET_TAG}"
tar -xzf "$PREVIOUS_BUNDLE" -C "$DEPLOY_DIR"
python3 "${DEPLOY_DIR}/render-monitoring-config.py"
write_env_value "$TARGET_TAG"

COMPOSE=(docker compose --env-file "$ENV_FILE" -f "${DEPLOY_DIR}/compose.yml")
if "${COMPOSE[@]}" pull \
  && "${COMPOSE[@]}" up --detach --remove-orphans --wait --wait-timeout 600 \
  && "${COMPOSE[@]}" up --detach --no-deps --force-recreate caddy alertmanager blackbox-exporter prometheus grafana \
  && "${COMPOSE[@]}" up --detach --remove-orphans --wait --wait-timeout 600; then
  install -m 600 "$CURRENT_BUNDLE" "$PREVIOUS_BUNDLE"
  printf '%s\n' "$CURRENT_TAG" > "$PREVIOUS_FILE"
  echo "StoxSim production rolled back to ${TARGET_TAG}"
  exit 0
fi

echo "Rollback failed; restoring the deployment that was active before rollback." >&2
tar -xzf "$CURRENT_BUNDLE" -C "$DEPLOY_DIR"
python3 "${DEPLOY_DIR}/render-monitoring-config.py"
write_env_value "$CURRENT_TAG"
COMPOSE=(docker compose --env-file "$ENV_FILE" -f "${DEPLOY_DIR}/compose.yml")
set +e
"${COMPOSE[@]}" pull
"${COMPOSE[@]}" up --detach --remove-orphans --wait --wait-timeout 600
"${COMPOSE[@]}" up --detach --no-deps --force-recreate caddy alertmanager blackbox-exporter prometheus grafana
exit 1
