#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
TMP_DIR=$(mktemp -d)
trap 'rm -rf "$TMP_DIR"' EXIT

mkdir -p "$TMP_DIR/deploy/backups"
cat > "$TMP_DIR/deploy/backup.sh" <<'MOCK'
#!/usr/bin/env bash
set -Eeuo pipefail

# Reproduce Docker Compose exec's default behavior: consume all attached stdin.
cat >/dev/null
if [[ "${MOCK_SKIP_NEW_BACKUP:-false}" == true ]]; then
  exit 0
fi
backup_file="$PWD/backups/stoxsim-20260823T120000Z.dump"
printf 'mock PostgreSQL custom archive\n' > "$backup_file"
(
  cd backups
  sha256sum "$(basename "$backup_file")" > "$(basename "${backup_file}.sha256")"
)
MOCK
chmod +x "$TMP_DIR/deploy/backup.sh"

target="backups/.stoxsim-recovery-12345.dump"
printf 'remote control commands that must not be consumed\n' \
  | bash "$ROOT_DIR/deploy/staging/prepare-recovery-backup.sh" "$TMP_DIR/deploy" "$target"

test -f "$TMP_DIR/deploy/$target"
test -f "$TMP_DIR/deploy/${target}.sha256"
(
  cd "$TMP_DIR/deploy/backups"
  sha256sum --check "$(basename "${target}.sha256")"
)

if printf 'stdin\n' | MOCK_SKIP_NEW_BACKUP=true \
    bash "$ROOT_DIR/deploy/staging/prepare-recovery-backup.sh" \
      "$TMP_DIR/deploy" 'backups/.stoxsim-recovery-12346.dump'; then
  echo "The recovery helper reused a stale backup after backup creation produced nothing." >&2
  exit 1
fi

if bash "$ROOT_DIR/deploy/staging/prepare-recovery-backup.sh" \
    "$TMP_DIR/deploy" 'backups/stoxsim-recovery-unsafe.dump'; then
  echo "The recovery helper accepted an unsafe backup target." >&2
  exit 1
fi

if grep -Fq 'bash -s' "$ROOT_DIR/scripts/staging-recovery-drill.sh"; then
  echo "The recovery drill must not stream its control script over SSH stdin." >&2
  exit 1
fi
grep -Fq 'exec --interactive=false -T postgres' "$ROOT_DIR/deploy/staging/backup.sh"
grep -Fq 'exec --interactive=false -T postgres' "$ROOT_DIR/deploy/production/backup.sh"

echo "Staging recovery stdin handoff safeguards passed"
