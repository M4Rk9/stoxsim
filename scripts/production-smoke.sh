#!/usr/bin/env bash
set -Eeuo pipefail

API_URL="${PRODUCTION_API_URL:?Set PRODUCTION_API_URL, for example https://api.stoxsim.com}"
WEB_URL="${PRODUCTION_WEB_URL:?Set PRODUCTION_WEB_URL, for example https://stoxsim.com}"
BASE_EMAIL="${PRODUCTION_SMOKE_EMAIL:?Set PRODUCTION_SMOKE_EMAIL to a monitored inbox}"
API_URL="${API_URL%/}"
WEB_URL="${WEB_URL%/}"
COOKIE_JAR=$(mktemp)
HEADER_FILE=$(mktemp)
REGISTER_RESPONSE_FILE=$(mktemp)
ACCESS_TOKEN=""
PASSWORD="Production-smoke-$(openssl rand -hex 12)"
local_part="${BASE_EMAIL%@*}"
domain_part="${BASE_EMAIL#*@}"
EMAIL="${local_part}+production-smoke-$(date +%s)-${RANDOM}@${domain_part}"

# A registration response may be lost after the database commit. Retain the
# generated credentials only in memory and recover a session for cleanup if needed.
ACCOUNT_MAY_EXIST=false
ACCOUNT_ID=""
CLEANUP_RESPONSE_FILE=$(mktemp)

cleanup_session() {
  local body status
  body=$(jq -nc --arg email "$EMAIL" --arg password "$PASSWORD" '{email: $email, password: $password}')
  status=$(curl --silent --show-error --connect-timeout 10 --max-time 20 \
    --request POST -H "Content-Type: application/json" -d "$body" \
    --output "$CLEANUP_RESPONSE_FILE" --write-out '%{http_code}' \
    "${API_URL}/api/v1/auth/login") || status=000
  if [[ "$status" != "200" ]]; then
    echo "::error::Could not recover the temporary account for cleanup; HTTP ${status}" >&2
    return 1
  fi
  ACCESS_TOKEN=$(jq -er '.accessToken | select(type == "string" and length > 20)' "$CLEANUP_RESPONSE_FILE") || return 1
}

delete_temporary_account() {
  local body status check_status
  [[ "$ACCOUNT_MAY_EXIST" == "true" ]] || return 0
  if [[ -z "$ACCESS_TOKEN" ]]; then
    cleanup_session || return 1
  fi
  body=$(jq -nc --arg password "$PASSWORD" '{password: $password}')
  for attempt in 1 2 3; do
    status=$(curl --silent --show-error --connect-timeout 10 --max-time 20 \
      --request DELETE -H "Authorization: Bearer ${ACCESS_TOKEN}" \
      -H "Content-Type: application/json" -d "$body" \
      --output "$CLEANUP_RESPONSE_FILE" --write-out '%{http_code}' \
      "${API_URL}/api/v1/auth/me") || status=000
    if [[ "$status" == "204" ]]; then
      check_status=$(curl --silent --show-error --connect-timeout 10 --max-time 20 \
        -H "Authorization: Bearer ${ACCESS_TOKEN}" --output "$CLEANUP_RESPONSE_FILE" \
        --write-out '%{http_code}' "${API_URL}/api/v1/auth/me") || check_status=000
      if [[ "$check_status" != "401" ]]; then
        echo "::error::Temporary account deletion could not be verified; HTTP ${check_status}" >&2
        return 1
      fi
      ACCOUNT_MAY_EXIST=false
      ACCESS_TOKEN=""
      echo "Temporary production smoke account removed and session rejection verified"
      return 0
    fi
    echo "::warning::Temporary account cleanup attempt ${attempt}/3 returned HTTP ${status}" >&2
    if [[ "$status" == "401" && "$attempt" -lt 3 ]]; then
      cleanup_session || return 1
    elif [[ ! "$status" =~ ^(000|429|500|502|503|504)$ ]]; then
      break
    fi
    [[ "$attempt" -eq 3 ]] || sleep $((attempt * 2))
  done
  return 1
}

cleanup() {
  local status=$?
  trap - EXIT
  set +e
  if ! delete_temporary_account; then
    echo "::error::Production smoke cleanup failed; run the private test-account audit on the VPS. Account ID: ${ACCOUNT_ID:-unavailable}" >&2
    if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
      printf '\n### Production smoke cleanup failed\nTemporary account ID: %s. Run the private VPS test-account audit.\n' \
        "${ACCOUNT_ID:-unavailable}" >> "$GITHUB_STEP_SUMMARY"
    fi
    [[ "$status" -ne 0 ]] || status=1
  fi
  rm -f "$COOKIE_JAR" "$HEADER_FILE" "$REGISTER_RESPONSE_FILE" "$CLEANUP_RESPONSE_FILE"
  exit "$status"
}
trap cleanup EXIT

retry() {
  local attempts=0
  until "$@"; do
    attempts=$((attempts + 1))
    if [[ "$attempts" -ge 12 ]]; then
      return 1
    fi
    sleep 5
  done
}

check_api_readiness() {
  local payload
  payload=$(curl --fail --silent --show-error "${API_URL}/actuator/health/readiness") || return 1
  jq -e '.status == "UP"' <<<"$payload" >/dev/null
}

check_web_readiness() {
  local payload
  payload=$(curl --fail --silent --show-error "$WEB_URL") || return 1
  grep -Fq "StoxSim" <<<"$payload"
}

echo "Checking production readiness"
retry check_api_readiness
retry check_web_readiness

# Read-only checks also run when public registration is intentionally closed.
bash "$(dirname "${BASH_SOURCE[0]}")/security-smoke.sh" "$WEB_URL" "$API_URL"

echo "Checking public legal pages and active contact"
for page in privacy terms cookies disclaimer; do
  payload=$(curl --fail --silent --show-error "${WEB_URL}/${page}")
  grep -Fq "support.stoxsim@gmail.com" <<<"$payload"
done

REGISTER_BODY=$(jq -nc \
  --arg displayName "Production Smoke" \
  --arg email "$EMAIL" \
  --arg password "$PASSWORD" \
  '{displayName: $displayName, email: $email, password: $password, termsAccepted: true}')

echo "Checking registration gate, secure cookie and virtual accounts"
: > "$HEADER_FILE"
: > "$REGISTER_RESPONSE_FILE"
ACCOUNT_MAY_EXIST=true
REGISTER_STATUS=$(curl --connect-timeout 10 --max-time 30 --silent --show-error \
  --dump-header "$HEADER_FILE" \
  --cookie-jar "$COOKIE_JAR" \
  --output "$REGISTER_RESPONSE_FILE" \
  --write-out '%{http_code}' \
  -H "Content-Type: application/json" \
  -d "$REGISTER_BODY" \
  "${API_URL}/api/v1/auth/register")
AUTH=$(cat "$REGISTER_RESPONSE_FILE")

if [[ "$REGISTER_STATUS" == "201" ]]; then
  ACCOUNT_ID=$(jq -r '.user.id // empty' <<<"$AUTH")
  [[ "$ACCOUNT_ID" =~ ^[0-9a-fA-F-]{36}$ ]] || ACCOUNT_ID=""
  ACCESS_TOKEN=$(jq -er '.accessToken | select(type == "string" and length > 20)' <<<"$AUTH")
  grep -Eiq '^set-cookie:.*stoxsim_refresh=' "$HEADER_FILE"
  grep -Eiq '^set-cookie:.*HttpOnly' "$HEADER_FILE"
  grep -Eiq '^set-cookie:.*Secure' "$HEADER_FILE"
  grep -Eiq '^set-cookie:.*SameSite=Strict' "$HEADER_FILE"
  jq -e '
    (.user.accounts | length) == 2
    and any(.user.accounts[]; .marketRegion == "INDIA" and .availableCash == 500000)
    and any(.user.accounts[]; .marketRegion == "UNITED_STATES" and .availableCash == 10000)
  ' <<<"$AUTH" >/dev/null

  echo "Checking authenticated identity and refresh rotation"
  ME=$(curl --fail --silent --show-error \
    -H "Authorization: Bearer ${ACCESS_TOKEN}" \
    "${API_URL}/api/v1/auth/me")
  jq -e --arg email "$EMAIL" '.email == $email' <<<"$ME" >/dev/null

  ROTATED=$(curl --fail --silent --show-error \
    --request POST \
    --cookie "$COOKIE_JAR" \
    --cookie-jar "$COOKIE_JAR" \
    "${API_URL}/api/v1/auth/refresh")
  NEW_ACCESS_TOKEN=$(jq -er '.accessToken | select(type == "string" and length > 20)' <<<"$ROTATED")
  ACCESS_TOKEN="$NEW_ACCESS_TOKEN"
  [[ "${#ACCESS_TOKEN}" -gt 20 ]]

  echo "Production smoke checks passed; verifying temporary account cleanup"
elif [[ "$REGISTER_STATUS" == "503" ]]; then
  if ! jq -e '
    .status == 503
    and (
      .message == "Public registration is temporarily closed"
      or .message == "Public registration is closed until market-data permissions are enabled"
    )
  ' <<<"$AUTH" >/dev/null; then
    echo "::error::Registration returned an unexpected HTTP 503 response"
    exit 1
  fi

  if grep -Eiq '^set-cookie:.*stoxsim_refresh=' "$HEADER_FILE"; then
    echo "::error::Registration gate returned a refresh cookie while registration is closed"
    exit 1
  fi

  ACCOUNT_MAY_EXIST=false
  echo "Public registration is intentionally closed by the release gate"
  echo "Production smoke checks passed in protected pre-release mode"
else
  echo "::error::Registration smoke check returned unexpected HTTP ${REGISTER_STATUS}"
  exit 1
fi
