#!/usr/bin/env bash
set -Eeuo pipefail

REPO_ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
SCRIPT="$REPO_ROOT/scripts/alpaca-preflight.sh"

TMP_DIR=$(mktemp -d)
trap 'rm -rf "$TMP_DIR"' EXIT

cat > "$TMP_DIR/curl" <<'SH'
#!/usr/bin/env bash
set -Eeuo pipefail

status="${MOCK_ALPACA_HTTP_STATUS:-200}"
output_file=""
url=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --output)
      output_file="$2"
      shift 2
      ;;
    --write-out)
      shift 2
      ;;
    -H|--connect-timeout|--max-time)
      shift 2
      ;;
    --silent|--show-error)
      shift
      ;;
    *)
      url="$1"
      shift
      ;;
  esac
done

if [[ -n "$output_file" && "$status" == "200" ]]; then
  if [[ "$url" == *"/v2/assets/SPY" ]]; then
    printf '{"symbol":"SPY","status":"active"}' > "$output_file"
  else
    printf '{"snapshots":{"SPY":{"latestTrade":{"p":550.25}}}}' > "$output_file"
  fi
fi

printf '%s' "$status"
SH
chmod +x "$TMP_DIR/curl"

env_base=(
  "PATH=$TMP_DIR:$PATH"
  "ALPACA_API_KEY_ID=test-key"
  "ALPACA_API_SECRET_KEY=test-secret"
  "ALPACA_PREFLIGHT_MAX_ATTEMPTS=2"
  "ALPACA_PREFLIGHT_RETRY_DELAY_SECONDS=0"
)

MOCK_ALPACA_HTTP_STATUS=500 env "${env_base[@]}" bash "$SCRIPT" > "$TMP_DIR/transient.log" 2>&1
grep -Fq "Alpaca asset preflight skipped after transient provider failures (HTTP 500); continuing deploy." "$TMP_DIR/transient.log"
grep -Fq "Alpaca snapshot preflight skipped after transient provider failures (HTTP 500); continuing deploy." "$TMP_DIR/transient.log"

set +e
MOCK_ALPACA_HTTP_STATUS=401 env "${env_base[@]}" bash "$SCRIPT" > "$TMP_DIR/auth.log" 2>&1
status=$?
set -e
if [[ "$status" -eq 0 ]]; then
  echo "Expected preflight to fail for HTTP 401"
  exit 1
fi
grep -Fq "Alpaca asset preflight failed with HTTP 401" "$TMP_DIR/auth.log"

MOCK_ALPACA_HTTP_STATUS=200 env "${env_base[@]}" bash "$SCRIPT" > "$TMP_DIR/success.log" 2>&1
grep -Fq "Alpaca credentials, asset catalogue and SPY snapshot are available" "$TMP_DIR/success.log"

echo "Alpaca preflight behavior checks passed"
