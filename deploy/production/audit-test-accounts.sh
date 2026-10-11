#!/usr/bin/env bash
set -Eeuo pipefail
DEPLOY_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
DETAILS=false
case "${1:-}" in
  "") ;;
  --details) DETAILS=true ;;
  *) echo "Usage: $0 [--details]" >&2; exit 2 ;;
esac
[[ "$#" -le 1 ]] || exit 2
# This is the documented monitored inbox, not a credential. For a different
# smoke inbox, supply PRODUCTION_SMOKE_EMAIL explicitly without reading .env.
BASE_EMAIL="${PRODUCTION_SMOKE_EMAIL:-support.stoxsim@gmail.com}"
echo "Read-only test-account audit; detail output contains private account identifiers."
docker compose --env-file "$DEPLOY_DIR/.env" -f "$DEPLOY_DIR/compose.yml" \
  exec -T -e 'PGOPTIONS=-c default_transaction_read_only=on -c statement_timeout=10000' \
  postgres sh -c 'exec psql -X -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" "$@"' \
  sh -v "smoke_base_email=$BASE_EMAIL" -v "show_details=$DETAILS" \
  < "$DEPLOY_DIR/audit-test-accounts.sql"
