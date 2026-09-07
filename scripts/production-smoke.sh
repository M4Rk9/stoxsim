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

cleanup() {
  local status=$?
  trap - EXIT
  set +e
  if [[ -n "$ACCESS_TOKEN" ]]; then
    DELETE_BODY=$(jq -nc --arg password "$PASSWORD" '{password: $password}')
    curl --silent --show-error \
      --request DELETE \
      -H "Authorization: Bearer ${ACCESS_TOKEN}" \
      -H "Content-Type: application/json" \
      -d "$DELETE_BODY" \
      "${API_URL}/api/v1/auth/me" >/dev/null
  fi
  rm -f "$COOKIE_JAR" "$HEADER_FILE" "$REGISTER_RESPONSE_FILE"
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
REGISTER_STATUS=$(curl --silent --show-error \
  --dump-header "$HEADER_FILE" \
  --cookie-jar "$COOKIE_JAR" \
  --output "$REGISTER_RESPONSE_FILE" \
  --write-out '%{http_code}' \
  -H "Content-Type: application/json" \
  -d "$REGISTER_BODY" \
  "${API_URL}/api/v1/auth/register")
AUTH=$(cat "$REGISTER_RESPONSE_FILE")

if [[ "$REGISTER_STATUS" == "201" ]]; then
  ACCESS_TOKEN=$(jq -er '.accessToken' <<<"$AUTH")
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
  ACCESS_TOKEN=$(jq -er '.accessToken' <<<"$ROTATED")
  [[ "${#ACCESS_TOKEN}" -gt 20 ]]

  echo "Production smoke checks passed; removing temporary account"
  DELETE_BODY=$(jq -nc --arg password "$PASSWORD" '{password: $password}')
  curl --fail --silent --show-error \
    --request DELETE \
    -H "Authorization: Bearer ${ACCESS_TOKEN}" \
    -H "Content-Type: application/json" \
    -d "$DELETE_BODY" \
    "${API_URL}/api/v1/auth/me" >/dev/null
  ACCESS_TOKEN=""
elif [[ "$REGISTER_STATUS" == "503" ]]; then
  if ! jq -e '
    .status == 503
    and (
      .message == "Public registration is temporarily closed"
      or .message == "Public registration is closed until market-data permissions are enabled"
    )
  ' <<<"$AUTH" >/dev/null; then
    echo "::error::Registration returned an unexpected HTTP 503 response"
    cat "$REGISTER_RESPONSE_FILE"
    exit 1
  fi

  if grep -Eiq '^set-cookie:.*stoxsim_refresh=' "$HEADER_FILE"; then
    echo "::error::Registration gate returned a refresh cookie while registration is closed"
    exit 1
  fi

  echo "Public registration is intentionally closed by the release gate"
  echo "Production smoke checks passed in protected pre-release mode"
else
  echo "::error::Registration smoke check returned unexpected HTTP ${REGISTER_STATUS}"
  cat "$REGISTER_RESPONSE_FILE"
  exit 1
fi
