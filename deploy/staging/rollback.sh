#!/usr/bin/env bash
set -Eeuo pipefail

DEPLOY_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
ENV_FILE="${DEPLOY_DIR}/.env"
PREVIOUS_FILE="${DEPLOY_DIR}/.previous-image-tag"

if [[ ! -f "$ENV_FILE" || ! -f "$PREVIOUS_FILE" ]]; then
  echo "A configured .env and .previous-image-tag are required to roll back." >&2
  exit 1
fi

TARGET_TAG=$(tr -d '[:space:]' < "$PREVIOUS_FILE")
if [[ ! "$TARGET_TAG" =~ ^[0-9a-f]{40}$ ]]; then
  echo "The previous image tag is not a valid immutable commit SHA." >&2
  exit 1
fi

CURRENT_TAG=$(sed -n 's/^STOXSIM_IMAGE_TAG=//p' "$ENV_FILE" | tail -n 1)
temporary=$(mktemp "${ENV_FILE}.XXXXXX")
trap 'rm -f "$temporary"' EXIT
awk -v value="$TARGET_TAG" '
  /^STOXSIM_IMAGE_TAG=/ { print "STOXSIM_IMAGE_TAG=" value; next }
  { print }
' "$ENV_FILE" > "$temporary"
chmod --reference="$ENV_FILE" "$temporary"
mv "$temporary" "$ENV_FILE"

COMPOSE=(docker compose --env-file "$ENV_FILE" -f "${DEPLOY_DIR}/compose.yml")
"${COMPOSE[@]}" pull
"${COMPOSE[@]}" up --detach --remove-orphans --wait --wait-timeout 240

if [[ "$CURRENT_TAG" =~ ^[0-9a-f]{40}$ && "$CURRENT_TAG" != "$TARGET_TAG" ]]; then
  printf '%s\n' "$CURRENT_TAG" > "$PREVIOUS_FILE"
fi

echo "StoxSim staging rolled back to ${TARGET_TAG}"
