#!/usr/bin/env bash
set -euo pipefail

API_URL="${STAGING_API_URL:?Set STAGING_API_URL, for example https://api.staging.stoxsim.com}"
WEB_URL="${STAGING_WEB_URL:?Set STAGING_WEB_URL, for example https://staging.stoxsim.com}"
API_URL="${API_URL%/}"
WEB_URL="${WEB_URL%/}"

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

echo "Checking staging readiness"
retry curl --fail --silent --show-error "${API_URL}/actuator/health/readiness" \
  | jq -e '.status == "UP"' >/dev/null
retry curl --fail --silent --show-error "${WEB_URL}" \
  | grep -q "StoxSim"

EMAIL="staging-smoke-$(date +%s)-${RANDOM}@stoxsim.test"
REGISTER_BODY=$(jq -nc \
  --arg displayName "Staging Smoke" \
  --arg email "$EMAIL" \
  --arg password "Staging-smoke-2026" \
  '{displayName: $displayName, email: $email, password: $password}')

echo "Checking registration and virtual-account creation"
AUTH=$(curl --fail --silent --show-error \
  -H "Content-Type: application/json" \
  -d "$REGISTER_BODY" \
  "${API_URL}/api/v1/auth/register")
ACCESS_TOKEN=$(jq -er '.accessToken' <<<"$AUTH")
REFRESH_TOKEN=$(jq -er '.refreshToken' <<<"$AUTH")
jq -e '
  (.user.accounts | length) == 2
  and any(.user.accounts[]; .marketRegion == "INDIA" and .availableCash == 500000)
  and any(.user.accounts[]; .marketRegion == "UNITED_STATES" and .availableCash == 10000)
' <<<"$AUTH" >/dev/null

echo "Checking authenticated API and token rotation"
curl --fail --silent --show-error \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  "${API_URL}/api/v1/auth/me" \
  | jq -e --arg email "$EMAIL" '.email == $email' >/dev/null

REFRESH_BODY=$(jq -nc --arg refreshToken "$REFRESH_TOKEN" '{refreshToken: $refreshToken}')
ROTATED=$(curl --fail --silent --show-error \
  -H "Content-Type: application/json" \
  -d "$REFRESH_BODY" \
  "${API_URL}/api/v1/auth/refresh")
jq -e '.accessToken | type == "string" and length > 20' <<<"$ROTATED" >/dev/null

echo "Staging smoke checks passed"
