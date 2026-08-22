#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
TMP_DIR=$(mktemp -d)
trap 'rm -rf "$TMP_DIR"' EXIT

mkdir -p "$TMP_DIR/bin" "$TMP_DIR/output"
cat > "$TMP_DIR/bin/curl" <<'MOCK'
#!/usr/bin/env bash
set -Eeuo pipefail

output=""
url=""
while (( $# > 0 )); do
  case "$1" in
    --output)
      output="${2:?curl mock requires an output path}"
      shift 2
      ;;
    --fail|--silent|--show-error)
      shift
      ;;
    *)
      url="$1"
      shift
      ;;
  esac
done

if [[ -z "$output" ]]; then
  echo "The readiness probe attempted to stream curl output." >&2
  exit 23
fi

case "$url" in
  */actuator/health/readiness)
    printf '{"status":"%s"}\n' "${MOCK_API_STATUS:-UP}" > "$output"
    ;;
  *)
    {
      printf '<html><title>%s</title><body>\n' "${MOCK_WEB_TITLE:-StoxSim}"
      head -c 1048576 /dev/zero | tr '\0' x
      printf '</body></html>\n'
    } > "$output"
    ;;
esac
MOCK
chmod +x "$TMP_DIR/bin/curl"

PATH="$TMP_DIR/bin:$PATH" \
  bash "$ROOT_DIR/scripts/staging-readiness.sh" \
    'https://api.staging.test' 'https://staging.test' "$TMP_DIR/output"

test -s "$TMP_DIR/output/readiness-api.json"
test -s "$TMP_DIR/output/readiness-web.html"

marker_line=$(grep -n '^MARKER_MAY_EXIST=true$' "$ROOT_DIR/scripts/staging-recovery-drill.sh" \
  | tail -n 1 | cut -d: -f1)
retry_line=$(grep -n '^retry_readiness$' "$ROOT_DIR/scripts/staging-recovery-drill.sh" \
  | tail -n 1 | cut -d: -f1)
if [[ -z "$marker_line" || -z "$retry_line" ]] || (( marker_line >= retry_line )); then
  echo "The restored marker is not cleanup-protected before readiness verification." >&2
  exit 1
fi

if PATH="$TMP_DIR/bin:$PATH" MOCK_API_STATUS=DOWN \
    bash "$ROOT_DIR/scripts/staging-readiness.sh" \
      'https://api.staging.test' 'https://staging.test' "$TMP_DIR/output"; then
  echo "The readiness probe accepted a DOWN API." >&2
  exit 1
fi

if PATH="$TMP_DIR/bin:$PATH" MOCK_WEB_TITLE=Unavailable \
    bash "$ROOT_DIR/scripts/staging-readiness.sh" \
      'https://api.staging.test' 'https://staging.test' "$TMP_DIR/output"; then
  echo "The readiness probe accepted a page without StoxSim." >&2
  exit 1
fi

echo "Staging readiness safeguards passed"
