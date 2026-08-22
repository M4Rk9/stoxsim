#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
TMP_DIR=$(mktemp -d)
trap 'rm -rf "$TMP_DIR"' EXIT

mkdir -p "$TMP_DIR/bin" "$TMP_DIR/deploy/backups"
touch "$TMP_DIR/deploy/.env" "$TMP_DIR/deploy/compose.yml"
printf 'mock PostgreSQL custom archive\n' > "$TMP_DIR/deploy/backups/recovery.dump"
(
  cd "$TMP_DIR/deploy/backups"
  sha256sum recovery.dump > recovery.dump.sha256
)

cat > "$TMP_DIR/bin/docker" <<'MOCK'
#!/usr/bin/env bash
set -Eeuo pipefail
printf '%s\n' "$*" >> "${MOCK_DOCKER_LOG:?}"

case "$*" in
  *"exec -T postgres pg_restore --list"*)
    cat >/dev/null
    ;;
  *"exec -T postgres sh -euc"*)
    cat >/dev/null
    if [[ "${MOCK_RESTORE_FAIL:-false}" == true ]]; then
      exit 73
    fi
    ;;
esac
MOCK
chmod +x "$TMP_DIR/bin/docker"

run_restore() {
  PATH="$TMP_DIR/bin:$PATH" \
  MOCK_DOCKER_LOG="$TMP_DIR/docker.log" \
  STOXSIM_DEPLOY_DIR="$TMP_DIR/deploy" \
  CONFIRM_STAGING_RESTORE=restore \
    bash "$ROOT_DIR/deploy/staging/restore.sh" "$TMP_DIR/deploy/backups/recovery.dump"
}

: > "$TMP_DIR/docker.log"
run_restore
grep -Fq 'stop caddy frontend backend' "$TMP_DIR/docker.log"
grep -Fq 'exec -T postgres sh -euc' "$TMP_DIR/docker.log"
grep -Fq 'up --detach --remove-orphans --wait --wait-timeout 600' "$TMP_DIR/docker.log"

: > "$TMP_DIR/docker.log"
if MOCK_RESTORE_FAIL=true run_restore; then
  echo "The restore failure-path test unexpectedly succeeded." >&2
  exit 1
fi
grep -Fq 'stop caddy frontend backend' "$TMP_DIR/docker.log"
grep -Fq 'up --detach --remove-orphans --wait --wait-timeout 600' "$TMP_DIR/docker.log"

echo "Staging restore safeguards passed"
