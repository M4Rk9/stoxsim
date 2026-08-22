#!/usr/bin/env bash
set -Eeuo pipefail

API_URL="${STAGING_API_URL:?Set STAGING_API_URL}"
WEB_URL="${STAGING_WEB_URL:?Set STAGING_WEB_URL}"
STAGING_HOST="${STAGING_HOST:?Set STAGING_HOST}"
STAGING_PORT="${STAGING_PORT:-22}"
STAGING_USER="${STAGING_USER:?Set STAGING_USER}"
REMOTE_DIR="${REMOTE_DIR:-stoxsim-staging}"
EVIDENCE_FILE="${EVIDENCE_FILE:-artifacts/restore-evidence.txt}"
RUN_ID="${GITHUB_RUN_ID:?Run this drill from GitHub Actions}"

API_URL="${API_URL%/}"
WEB_URL="${WEB_URL%/}"
if [[ "$API_URL" != https://* || "$WEB_URL" != https://* ]]; then
  echo "Staging recovery verification requires HTTPS web and API URLs." >&2
  exit 2
fi
if [[ ! "$STAGING_PORT" =~ ^[0-9]+$ ]] || (( STAGING_PORT < 1 || STAGING_PORT > 65535 )); then
  echo "STAGING_PORT must be an integer from 1 through 65535." >&2
  exit 2
fi
if [[ ! "$REMOTE_DIR" =~ ^[A-Za-z0-9._/-]+$ ]]; then
  echo "REMOTE_DIR contains unsupported characters." >&2
  exit 2
fi
if [[ ! "$RUN_ID" =~ ^[0-9]+$ ]]; then
  echo "GITHUB_RUN_ID must contain only digits." >&2
  exit 2
fi

mkdir -p "$(dirname "$EVIDENCE_FILE")"
TMP_DIR=$(mktemp -d)
PASSWORD="Stoxsim-recovery-2026"
EMAIL="recovery-$(date +%s)-${RANDOM}@stoxsim.test"
ACCESS_TOKEN=""
MARKER_MAY_EXIST=false
REMOTE_DRILL_BACKUP="backups/.stoxsim-recovery-${RUN_ID}.dump"
REMOTE_PREPARE_SCRIPT="/tmp/stoxsim-prepare-recovery-${RUN_ID}.sh"
REMOTE_RESTORE_SCRIPT="/tmp/stoxsim-restore-${RUN_ID}.sh"
SSH=(ssh -p "$STAGING_PORT" -o ServerAliveInterval=30 -o ServerAliveCountMax=20 "$STAGING_USER@$STAGING_HOST")
SCP=(scp -P "$STAGING_PORT")

cleanup() {
  "${SSH[@]}" \
    "cd '$REMOTE_DIR' && rm -f '$REMOTE_DRILL_BACKUP' '${REMOTE_DRILL_BACKUP}.sha256' '${REMOTE_DRILL_BACKUP}.partial' .stoxsim-recovery-backup; rm -f '$REMOTE_PREPARE_SCRIPT' '$REMOTE_RESTORE_SCRIPT'" \
    >/dev/null 2>&1 || true
  if [[ "$MARKER_MAY_EXIST" == true ]]; then
    if [[ -z "$ACCESS_TOKEN" ]]; then
      curl --silent --show-error \
        --header "Content-Type: application/json" \
        --data "$(jq -nc --arg email "$EMAIL" --arg password "$PASSWORD" '{email: $email, password: $password}')" \
        "$API_URL/api/v1/auth/login" \
        | jq -er '.accessToken' > "$TMP_DIR/cleanup-token" 2>/dev/null || true
      ACCESS_TOKEN=$(cat "$TMP_DIR/cleanup-token" 2>/dev/null || true)
    fi
    if [[ -n "$ACCESS_TOKEN" ]]; then
      curl --silent --show-error \
        --request DELETE \
        --header "Authorization: Bearer $ACCESS_TOKEN" \
        --header "Content-Type: application/json" \
        --data "$(jq -nc --arg password "$PASSWORD" '{password: $password}')" \
        "$API_URL/api/v1/auth/me" >/dev/null || true
    fi
  fi
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT

echo "Uploading versioned recovery helpers"
"${SCP[@]}" deploy/staging/prepare-recovery-backup.sh \
  "$STAGING_USER@$STAGING_HOST:$REMOTE_PREPARE_SCRIPT"
"${SCP[@]}" deploy/staging/restore.sh \
  "$STAGING_USER@$STAGING_HOST:$REMOTE_RESTORE_SCRIPT"
"${SSH[@]}" "chmod 700 '$REMOTE_PREPARE_SCRIPT' '$REMOTE_RESTORE_SCRIPT'"

request_status() {
  local method="$1"
  local path="$2"
  local body="$3"
  local output="$4"
  local token="${5:-}"
  local args=(
    --silent --show-error
    --request "$method"
    --output "$output"
    --write-out '%{http_code}'
    --header "Content-Type: application/json"
  )
  if [[ -n "$token" ]]; then
    args+=(--header "Authorization: Bearer $token")
  fi
  if [[ -n "$body" ]]; then
    args+=(--data "$body")
  fi
  curl "${args[@]}" "$API_URL$path"
}

retry_readiness() {
  local attempts=0
  until curl --fail --silent --show-error "$API_URL/actuator/health/readiness" \
      | jq -e '.status == "UP"' >/dev/null \
    && curl --fail --silent --show-error "$WEB_URL" | grep -Fq "StoxSim"; do
    attempts=$((attempts + 1))
    if (( attempts >= 24 )); then
      echo "Staging did not recover within two minutes." >&2
      return 1
    fi
    sleep 5
  done
}

echo "Creating a database marker before backup"
REGISTER_BODY=$(jq -nc \
  --arg displayName "Recovery Drill" \
  --arg email "$EMAIL" \
  --arg password "$PASSWORD" \
  '{displayName: $displayName, email: $email, password: $password, termsAccepted: true}')
STATUS=$(request_status POST /api/v1/auth/register "$REGISTER_BODY" "$TMP_DIR/register.json")
if [[ "$STATUS" != "201" ]]; then
  echo "Could not create the recovery marker (HTTP $STATUS)." >&2
  exit 1
fi
ACCESS_TOKEN=$(jq -er '.accessToken' "$TMP_DIR/register.json")
MARKER_MAY_EXIST=true

echo "Creating and verifying the staging backup"
"${SSH[@]}" \
  "'$REMOTE_PREPARE_SCRIPT' '$REMOTE_DIR' '$REMOTE_DRILL_BACKUP'"

echo "Applying the pre-restore verification barrier"
"${SSH[@]}" \
  "cd '$REMOTE_DIR' && test -f '$REMOTE_DRILL_BACKUP' && test -f '${REMOTE_DRILL_BACKUP}.sha256' && (cd backups && sha256sum --check '$(basename "${REMOTE_DRILL_BACKUP}.sha256")') && docker compose --env-file .env -f compose.yml exec -T postgres pg_restore --list < '$REMOTE_DRILL_BACKUP' >/dev/null"
echo "Recovery backup checksum and PostgreSQL archive validated"

echo "Deleting the marker before restore"
DELETE_BODY=$(jq -nc --arg password "$PASSWORD" '{password: $password}')
STATUS=$(request_status DELETE /api/v1/auth/me "$DELETE_BODY" "$TMP_DIR/delete.json" "$ACCESS_TOKEN")
if [[ "$STATUS" != "204" ]]; then
  echo "Could not delete the pre-restore marker (HTTP $STATUS)." >&2
  exit 1
fi
ACCESS_TOKEN=""
MARKER_MAY_EXIST=false

STARTED_AT=$(date -u +%Y-%m-%dT%H:%M:%SZ)
echo "Restoring the verified staging backup"
"${SSH[@]}" \
  "cd '$REMOTE_DIR' && STOXSIM_DEPLOY_DIR=. CONFIRM_STAGING_RESTORE=restore '$REMOTE_RESTORE_SCRIPT' '$REMOTE_DRILL_BACKUP'"
retry_readiness
MARKER_MAY_EXIST=true

"${SSH[@]}" \
  "cd '$REMOTE_DIR' && rm -f '$REMOTE_DRILL_BACKUP' '${REMOTE_DRILL_BACKUP}.sha256'; rm -f '$REMOTE_PREPARE_SCRIPT' '$REMOTE_RESTORE_SCRIPT'"

echo "Proving that the deleted marker returned from the backup"
LOGIN_BODY=$(jq -nc --arg email "$EMAIL" --arg password "$PASSWORD" '{email: $email, password: $password}')
STATUS=$(request_status POST /api/v1/auth/login "$LOGIN_BODY" "$TMP_DIR/login.json")
if [[ "$STATUS" != "200" ]]; then
  echo "The restored recovery marker could not sign in (HTTP $STATUS)." >&2
  exit 1
fi
ACCESS_TOKEN=$(jq -er '.accessToken' "$TMP_DIR/login.json")
STATUS=$(request_status GET /api/v1/auth/me "" "$TMP_DIR/me.json" "$ACCESS_TOKEN")
if [[ "$STATUS" != "200" ]] || ! jq -e --arg email "$EMAIL" '.email == $email' "$TMP_DIR/me.json" >/dev/null; then
  echo "The restored marker failed its authenticated identity check." >&2
  exit 1
fi

STATUS=$(request_status DELETE /api/v1/auth/me "$DELETE_BODY" "$TMP_DIR/final-delete.json" "$ACCESS_TOKEN")
if [[ "$STATUS" != "204" ]]; then
  echo "Could not remove the restored recovery marker (HTTP $STATUS)." >&2
  exit 1
fi
ACCESS_TOKEN=""
MARKER_MAY_EXIST=false
FINISHED_AT=$(date -u +%Y-%m-%dT%H:%M:%SZ)

{
  echo "StoxSim staging backup/restore drill"
  echo "started_at=$STARTED_AT"
  echo "finished_at=$FINISHED_AT"
  echo "backup_reference=pinned-and-verified-on-staging-host-for-run-${RUN_ID}"
  echo "checksum=verified"
  echo "restored_marker_login=passed"
  echo "post_restore_readiness=passed"
  echo "cleanup=passed"
} > "$EVIDENCE_FILE"

echo "Staging backup/restore drill passed"
