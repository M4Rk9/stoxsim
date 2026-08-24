#!/usr/bin/env bash
set -Eeuo pipefail

WORKFLOW="${1:-.github/workflows/production-uptime.yml}"

if [[ ! -f "$WORKFLOW" ]]; then
  echo "Production uptime workflow not found: $WORKFLOW" >&2
  exit 1
fi

if grep -Eq 'curl[^|]*\|[[:space:]]*grep' "$WORKFLOW"; then
  echo "Production uptime workflow must not pipe curl directly into grep" >&2
  exit 1
fi

grep -Fq -- '--output "$web_body"' "$WORKFLOW"
grep -Fq 'grep -Fq "StoxSim" "$web_body"' "$WORKFLOW"

echo "Production uptime workflow uses a pipe-safe response file"
