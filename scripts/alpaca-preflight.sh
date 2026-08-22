#!/usr/bin/env bash
set -Eeuo pipefail

: "${ALPACA_API_KEY_ID:?Set ALPACA_API_KEY_ID}"
: "${ALPACA_API_SECRET_KEY:?Set ALPACA_API_SECRET_KEY}"

ALPACA_DATA_FEED=${ALPACA_DATA_FEED:-iex}
TMP_DIR=$(mktemp -d)
trap 'rm -rf "$TMP_DIR"' EXIT

AUTH_HEADERS=(
  -H "APCA-API-KEY-ID: ${ALPACA_API_KEY_ID}"
  -H "APCA-API-SECRET-KEY: ${ALPACA_API_SECRET_KEY}"
)

curl --fail-with-body --silent --show-error \
  --retry 5 \
  --retry-delay 2 \
  --retry-max-time 90 \
  "${AUTH_HEADERS[@]}" \
  "https://paper-api.alpaca.markets/v2/assets/SPY" \
  -o "$TMP_DIR/asset.json"

curl --fail-with-body --silent --show-error \
  --retry 5 \
  --retry-delay 2 \
  --retry-max-time 90 \
  "${AUTH_HEADERS[@]}" \
  "https://data.alpaca.markets/v2/stocks/snapshots?symbols=SPY&feed=${ALPACA_DATA_FEED}" \
  -o "$TMP_DIR/snapshot.json"

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
