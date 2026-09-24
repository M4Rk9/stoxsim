#!/usr/bin/env bash
set -Eeuo pipefail
[[ "${GITHUB_ACTIONS:-}" == true && "${COMPOSE_PROJECT_NAME:-}" =~ ^stoxsim-ci-[0-9]+-[0-9]+$ ]] || {
  echo 'Only the isolated CI stack is supported' >&2; exit 1;
}
mkdir -p candidate-evidence
backup=$(mktemp)
source_rows=$(mktemp)
restored_rows=$(mktemp)
cleanup() {
  docker compose exec -T postgres dropdb -U stoxsim --if-exists m7_restore >/dev/null || true
  rm -f "$backup" "$source_rows" "$restored_rows"
}
trap cleanup EXIT
# Quiesce the disposable source so the comparison and dump refer to identical data.
docker compose stop backend frontend >/dev/null
started=$SECONDS
docker compose exec -T postgres pg_dump -U stoxsim -d stoxsim -Fc --no-owner --no-privileges > "$backup"
docker compose exec -T postgres createdb -U stoxsim m7_restore
docker compose exec -T postgres pg_restore -U stoxsim -d m7_restore --single-transaction --exit-on-error --no-owner --no-privileges < "$backup"
fingerprint() {
  docker compose exec -T postgres psql -X -U stoxsim -d "$1" -At -v ON_ERROR_STOP=1 <<'SQL'
SELECT format('SELECT %L, count(*), md5(coalesce(string_agg(to_jsonb(t)::text, '''' ORDER BY to_jsonb(t)::text), '''')) FROM %I.%I t;', tablename, schemaname, tablename)
FROM pg_tables WHERE schemaname='public' ORDER BY tablename
\gexec
SQL
}
fingerprint stoxsim > "$source_rows"
fingerprint m7_restore > "$restored_rows"
cmp --silent "$source_rows" "$restored_rows"
CANDIDATE_RESTORE_SECONDS=$((SECONDS-started)) CANDIDATE_RESTORE_TABLES=$(wc -l < "$source_rows") python3 - <<'PY'
import json, os
from pathlib import Path
Path('candidate-evidence/restore.json').write_text(json.dumps({
 'candidate_sha': os.environ['CANDIDATE_SHA'], 'passed': True,
 'public_tables_compared': int(os.environ['CANDIDATE_RESTORE_TABLES']),
 'elapsed_seconds': int(os.environ['CANDIDATE_RESTORE_SECONDS']),
 'scope': 'Disposable CI database; canonical row fingerprints match after custom-format backup/restore. Not a production backup or RTO claim.'
}, indent=2)+'\n')
PY
cat candidate-evidence/restore.json
