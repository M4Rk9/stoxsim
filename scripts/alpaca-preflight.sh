#!/usr/bin/env bash
set -Eeuo pipefail

: "${ALPACA_API_KEY_ID:?Set ALPACA_API_KEY_ID}"
: "${ALPACA_API_SECRET_KEY:?Set ALPACA_API_SECRET_KEY}"

ALPACA_DATA_FEED=${ALPACA_DATA_FEED:-iex}
ALPACA_PREFLIGHT_MAX_ATTEMPTS=${ALPACA_PREFLIGHT_MAX_ATTEMPTS:-5}
ALPACA_PREFLIGHT_RETRY_DELAY_SECONDS=${ALPACA_PREFLIGHT_RETRY_DELAY_SECONDS:-2}
TMP_DIR=$(mktemp -d)
trap 'rm -rf "$TMP_DIR"' EXIT

AUTH_HEADERS=(
  -H "APCA-API-KEY-ID: ${ALPACA_API_KEY_ID}"
  -H "APCA-API-SECRET-KEY: ${ALPACA_API_SECRET_KEY}"
)

fetch_with_transient_tolerance() {
  local request_name="$1"
  local url="$2"
  local output_file="$3"
  local last_http_status=000

  for ((attempt = 1; attempt <= ALPACA_PREFLIGHT_MAX_ATTEMPTS; attempt++)); do
    : > "$output_file"
    http_status=$(curl --silent --show-error \
      --connect-timeout 10 \
      --max-time 90 \
      --output "$output_file" \
      --write-out '%{http_code}' \
      "${AUTH_HEADERS[@]}" \
      "$url") || http_status=000
    last_http_status="$http_status"

    if [[ "$http_status" =~ ^2 ]]; then
      return 0
    fi
    if [[ "$http_status" =~ ^(401|403|404)$ ]]; then
      echo "::error::Alpaca ${request_name} preflight failed with HTTP ${http_status}"
      # Provider responses may echo credentials; log only the HTTP status above.
      return 1
    fi
    [[ "$attempt" -lt "$ALPACA_PREFLIGHT_MAX_ATTEMPTS" ]] && sleep "$ALPACA_PREFLIGHT_RETRY_DELAY_SECONDS"
  done

  if [[ "$last_http_status" =~ ^(000|408|429|5) ]]; then
    echo "::warning::Alpaca ${request_name} preflight skipped after transient provider failures (HTTP ${last_http_status}); continuing deploy."
    return 2
  fi

  echo "::error::Alpaca ${request_name} preflight failed with HTTP ${last_http_status}"
  # Provider responses may echo credentials; log only the HTTP status above.
  return 1
}

asset_result=0
snapshot_result=0

fetch_with_transient_tolerance \
  "asset" \
  "https://paper-api.alpaca.markets/v2/assets/SPY" \
  "$TMP_DIR/asset.json" || asset_result=$?

fetch_with_transient_tolerance \
  "snapshot" \
  "https://data.alpaca.markets/v2/stocks/snapshots?symbols=SPY&feed=${ALPACA_DATA_FEED}" \
  "$TMP_DIR/snapshot.json" || snapshot_result=$?

if [[ "$asset_result" -eq 1 || "$snapshot_result" -eq 1 ]]; then
  exit 1
fi

if [[ "$asset_result" -eq 2 || "$snapshot_result" -eq 2 ]]; then
  exit 0
fi

python3 - "$TMP_DIR/asset.json" "$TMP_DIR/snapshot.json" <<'PY'
import json
import sys
from pathlib import Path

asset = json.loads(Path(sys.argv[1]).read_text())
if asset.get("symbol") != "SPY" or asset.get("status") != "active":
    raise SystemExit("Alpaca asset preflight did not return an active SPY asset")

root = json.loads(Path(sys.argv[2]).read_text())
snapshots = root.get("snapshots", root)
snapshot = snapshots.get("SPY") if isinstance(snapshots, dict) else None
if not isinstance(snapshot, dict):
    raise SystemExit("Alpaca data preflight did not return a SPY snapshot")

candidates = [
    snapshot.get("latestTrade", {}).get("p"),
    snapshot.get("minuteBar", {}).get("c"),
    snapshot.get("dailyBar", {}).get("c"),
]
if not any(isinstance(value, (int, float)) and value > 0 for value in candidates):
    raise SystemExit("Alpaca SPY snapshot contained no usable price")

print("Alpaca credentials, asset catalogue and SPY snapshot are available")
PY
