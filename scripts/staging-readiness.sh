#!/usr/bin/env bash

check_staging_readiness() {
  local api_url="${1:?Pass the staging API URL}"
  local web_url="${2:?Pass the staging web URL}"
  local output_dir="${3:?Pass a private output directory}"
  local api_response="$output_dir/readiness-api.json"
  local web_response="$output_dir/readiness-web.html"

  curl --fail --silent --show-error \
    --output "$api_response" \
    "$api_url/actuator/health/readiness" \
    && jq -e '.status == "UP"' "$api_response" >/dev/null \
    && curl --fail --silent --show-error \
      --output "$web_response" \
      "$web_url" \
    && grep -Fq "StoxSim" "$web_response"
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  set -Eeuo pipefail
  check_staging_readiness "$@"
fi
