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

check_api_readiness() {
  local payload
  payload=$(curl --fail --silent --show-error "${API_URL}/actuator/health/readiness") || return 1
  jq -e '.status == "UP"' <<<"$payload" >/dev/null
}

check_web_readiness() {
  local payload
  payload=$(curl --fail --silent --show-error "${WEB_URL}") || return 1
  grep -Fq "StoxSim" <<<"$payload"
}

echo "Checking staging readiness"
retry check_api_readiness
retry check_web_readiness

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
ME=$(curl --fail --silent --show-error \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  "${API_URL}/api/v1/auth/me")
jq -e --arg email "$EMAIL" '.email == $email' <<<"$ME" >/dev/null

FINWIZ_BODY=$(jq -nc '{
  question: "Explain operating cash flow to a beginner in one short paragraph.",
  topic: "CASH_FLOW",
  experienceLevel: "BEGINNER"
}')
echo "Checking Gemini-backed Finwiz response"
FINWIZ=""
FINWIZ_OK=false
for attempt in 1 2 3; do
  FINWIZ=$(curl --fail --silent --show-error \
    -H "Authorization: Bearer ${ACCESS_TOKEN}" \
    -H "Content-Type: application/json" \
    -d "$FINWIZ_BODY" \
    "${API_URL}/api/v1/finwiz/ask")

  if jq -e '
    .provider == "GEMINI"
    and (.model | startswith("gemini-"))
    and (.answer | type == "string" and length > 40)
  ' <<<"$FINWIZ" >/dev/null; then
    FINWIZ_OK=true
    break
  fi

  echo "Finwiz attempt ${attempt} did not use Gemini: provider=$(jq -r '.provider // "missing"' <<<"$FINWIZ"), model=$(jq -r '.model // "missing"' <<<"$FINWIZ")"
  if [[ "$attempt" -lt 3 ]]; then
    sleep 10
  fi
done

if [[ "$FINWIZ_OK" != true ]]; then
  echo "::error::Finwiz returned a fallback or unusable Gemini response after three attempts. Expand 'Capture backend provider diagnostics' for the exact Gemini HTTP error."
  jq '{provider, model, groundedInStoxSimData, generatedAt, answerPreview: (.answer // "" | .[0:160])}' <<<"$FINWIZ"
  exit 1
fi

REFRESH_BODY=$(jq -nc --arg refreshToken "$REFRESH_TOKEN" '{refreshToken: $refreshToken}')
ROTATED=$(curl --fail --silent --show-error \
  -H "Content-Type: application/json" \
  -d "$REFRESH_BODY" \
  "${API_URL}/api/v1/auth/refresh")
jq -e '.accessToken | type == "string" and length > 20' <<<"$ROTATED" >/dev/null

echo "Staging smoke checks passed"
