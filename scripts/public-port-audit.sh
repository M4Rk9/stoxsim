#!/usr/bin/env bash
set -Eeuo pipefail

TARGET_HOST="${1:-}"
SSH_PORT="${2:-22}"
if [[ -z "$TARGET_HOST" ]]; then
  echo "Usage: $0 <public-hostname-or-ip> [ssh-port]" >&2
  exit 2
fi
if [[ ! "$SSH_PORT" =~ ^[0-9]+$ ]] || (( SSH_PORT < 1 || SSH_PORT > 65535 )); then
  echo "SSH port must be an integer from 1 through 65535" >&2
  exit 2
fi
if ! command -v nmap >/dev/null 2>&1; then
  echo "::error::nmap is required for the complete TCP exposure audit" >&2
  exit 2
fi

RESULT_FILE=$(mktemp)
cleanup() {
  rm -f "$RESULT_FILE"
}
trap cleanup EXIT

echo "Scanning the complete public TCP range"
if ! nmap \
  -Pn \
  -p 1-65535 \
  --open \
  --reason \
  --max-retries 2 \
  --host-timeout 12m \
  -T4 \
  -oX "$RESULT_FILE" \
  "$TARGET_HOST" \
  >/dev/null; then
  echo "::error::The complete TCP port scan did not finish successfully" >&2
  exit 1
fi

RESULT_FILE="$RESULT_FILE" SSH_PORT="$SSH_PORT" python3 - <<'PY'
import os
import sys
import xml.etree.ElementTree as ET

result_file = os.environ["RESULT_FILE"]
ssh_port = int(os.environ["SSH_PORT"])

try:
    root = ET.parse(result_file).getroot()
except (ET.ParseError, OSError) as error:
    print(f"::error::Could not parse nmap evidence: {error}", file=sys.stderr)
    raise SystemExit(1)

hosts = [host for host in root.findall("host") if host.find("status") is not None]
if not hosts or not any(host.find("status").get("state") == "up" for host in hosts):
    print("::error::The approved deployment host did not respond to the TCP audit", file=sys.stderr)
    raise SystemExit(1)

open_ports = {
    int(port.get("portid"))
    for host in hosts
    for port in host.findall("./ports/port")
    if port.get("protocol") == "tcp"
    and port.find("state") is not None
    and port.find("state").get("state") == "open"
}
allowed = {80, 443, ssh_port}
missing = allowed - open_ports
unexpected = open_ports - allowed

print("Open TCP ports: " + (", ".join(map(str, sorted(open_ports))) or "none"))
for port in sorted(missing):
    role = "configured deployment SSH" if port == ssh_port else "required Caddy"
    print(f"::error::TCP {port} ({role}) is not publicly reachable", file=sys.stderr)
for port in sorted(unexpected):
    print(f"::error::TCP {port} (unexpected service) is publicly reachable", file=sys.stderr)

if missing or unexpected:
    raise SystemExit(1)

print("Complete TCP exposure matches the approved web-plus-deployment-SSH contract")
PY
