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

TARGET_HOST="$TARGET_HOST" SSH_PORT="$SSH_PORT" python3 - <<'PY'
import os
import socket
import sys

target = os.environ["TARGET_HOST"]
ssh_port = int(os.environ["SSH_PORT"])
required_open = (80, 443)
required_closed = tuple(sorted({22, ssh_port, 3000, 3001, 5432, 6379, 8080, 9090, 9093}))

try:
    addresses = sorted({
        item[4][0]
        for item in socket.getaddrinfo(
            target,
            None,
            family=socket.AF_UNSPEC,
            type=socket.SOCK_STREAM,
        )
    })
except socket.gaierror as error:
    print(f"::error::Could not resolve the approved deployment host: {error}", file=sys.stderr)
    raise SystemExit(1)

if not addresses:
    print("::error::The approved deployment host resolved to no addresses", file=sys.stderr)
    raise SystemExit(1)

def connects(address: str, port: int) -> bool:
    family = socket.AF_INET6 if ":" in address else socket.AF_INET
    for _ in range(2):
        try:
            with socket.socket(family, socket.SOCK_STREAM) as connection:
                connection.settimeout(3)
                connection.connect((address, port))
                return True
        except (TimeoutError, ConnectionRefusedError, OSError):
            pass
    return False

failures = []
for port in required_open:
    is_open = any(connects(address, port) for address in addresses)
    print(f"TCP {port}: {'open' if is_open else 'closed/filtered'} (required open)")
    if not is_open:
        failures.append(f"TCP {port} is not publicly reachable")

for port in required_closed:
    is_open = any(connects(address, port) for address in addresses)
    print(f"TCP {port}: {'OPEN' if is_open else 'closed/filtered'} (required closed)")
    if is_open:
        failures.append(f"TCP {port} is publicly reachable")

if failures:
    for failure in failures:
        print(f"::error::{failure}", file=sys.stderr)
    raise SystemExit(1)

print("Public port exposure matches the Caddy-only contract")
PY
