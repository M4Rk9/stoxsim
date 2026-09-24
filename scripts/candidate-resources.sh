#!/usr/bin/env bash
set -Eeuo pipefail
[[ "${GITHUB_ACTIONS:-}" == true && "${COMPOSE_PROJECT_NAME:-}" =~ ^stoxsim-ci-[0-9]+-[0-9]+$ ]] || exit 1
mkdir -p candidate-evidence
while true; do
  docker stats --no-stream --format '{{json .}}' $(docker compose ps -q) >> candidate-evidence/resources.jsonl
  sleep 5
done
