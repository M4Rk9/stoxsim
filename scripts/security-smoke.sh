#!/usr/bin/env bash
set -euo pipefail

if [ "$#" -ne 2 ]; then
  echo "Usage: $0 <web-base-url> <api-base-url>" >&2
  exit 2
fi

WEB_URL="${1%/}"
API_URL="${2%/}"

headers() {
  curl --fail --silent --show-error --head "$1" | tr -d '\r'
}

header_value() {
  local response="$1"
  local name="$2"
  printf '%s\n' "$response" | awk -F': *' -v header="$name" '
    tolower($1) == tolower(header) {
      sub(/^[^:]*: */, "")
      print
      exit
    }
  '
}

require_header() {
  local response="$1"
  local name="$2"
  local expected="$3"
  local value
  value="$(header_value "$response" "$name")"
  if [ -z "$value" ] || [[ "$value" != *"$expected"* ]]; then
    echo "Missing or invalid $name header; expected to contain: $expected" >&2
    exit 1
  fi
}

forbid_header() {
  local response="$1"
  local name="$2"
  local value
  value="$(header_value "$response" "$name")"
  if [ -n "$value" ]; then
    echo "Unexpected $name response header" >&2
    exit 1
  fi
}

validate_csp_sources() {
  local response="$1"
  local csp
  csp="$(header_value "$response" "Content-Security-Policy")"

  require_header "$response" "Content-Security-Policy" "img-src 'self' data:"
  if [[ "$csp" =~ img-src[^\;]*https: ]] || [[ "$csp" =~ img-src[^\;]*\* ]]; then
    echo "Content-Security-Policy img-src must not allow broad HTTPS or wildcard sources" >&2
    exit 1
  fi
  if [[ "$csp" =~ script-src[^\;]*https: ]] || [[ "$csp" =~ script-src[^\;]*\* ]]; then
    echo "Content-Security-Policy script-src must not allow broad HTTPS or wildcard sources" >&2
    exit 1
  fi
}

expect_status() {
  local expected="$1"
  local url="$2"
  shift 2
  local actual
  actual="$(curl --silent --show-error --output /dev/null     --write-out '%{http_code}' "$@" "$url")"
  if [ "$actual" != "$expected" ]; then
    echo "Expected HTTP $expected from $url, received $actual" >&2
    exit 1
  fi
}

echo "Checking browser security headers"
WEB_HEADERS="$(headers "$WEB_URL/")"
require_header "$WEB_HEADERS" "Strict-Transport-Security" "max-age=31536000"
require_header "$WEB_HEADERS" "Content-Security-Policy" "frame-ancestors 'none'"
require_header "$WEB_HEADERS" "X-Content-Type-Options" "nosniff"
require_header "$WEB_HEADERS" "X-Frame-Options" "DENY"
require_header "$WEB_HEADERS" "Referrer-Policy" "strict-origin-when-cross-origin"
require_header "$WEB_HEADERS" "Permissions-Policy" "camera=()"
forbid_header "$WEB_HEADERS" "X-Powered-By"
validate_csp_sources "$WEB_HEADERS"

echo "Checking protected API behavior"
expect_status 401 "$API_URL/api/v1/portfolio"
expect_status 401 "$API_URL/api/v1/portfolio"   --header "Authorization: Bearer invalid-security-test-token"
expect_status 404 "$API_URL/actuator/prometheus"

echo "Checking auth responses are not cacheable"
AUTH_HEADERS="$(curl --silent --show-error --head "$API_URL/api/v1/auth/login" | tr -d '\r')"
require_header "$AUTH_HEADERS" "Cache-Control" "no-store"

echo "Checking an untrusted CORS origin is not reflected"
CORS_HEADERS="$(curl --silent --show-error --dump-header - --output /dev/null   --request OPTIONS   --header "Origin: https://attacker.invalid"   --header "Access-Control-Request-Method: GET"   "$API_URL/api/v1/portfolio" | tr -d '\r')"
if printf '%s\n' "$CORS_HEADERS" | grep -qi '^Access-Control-Allow-Origin:'; then
  echo "The API reflected an untrusted CORS origin" >&2
  exit 1
fi

echo "Security smoke checks passed for $WEB_URL and $API_URL"
