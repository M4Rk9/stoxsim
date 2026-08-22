#!/usr/bin/env bash
set -Eeuo pipefail

DEPLOY_DIR="${1:?Pass the staging deployment directory}"
TARGET_BACKUP="${2:?Pass the run-specific recovery backup path}"

if [[ ! "$DEPLOY_DIR" =~ ^[A-Za-z0-9._/-]+$ ]]; then
  echo "The staging deployment directory contains unsupported characters." >&2
  exit 2
fi
if [[ ! "$TARGET_BACKUP" =~ ^backups/\.stoxsim-recovery-[0-9]+\.dump$ ]]; then
  echo "The recovery backup path is not run-specific or has an unsafe format." >&2
  exit 2
fi

cd "$DEPLOY_DIR"

echo "Creating the timestamped staging backup"
backup_started=$(mktemp "$PWD/backups/.stoxsim-backup-started.XXXXXX")
trap 'rm -f "$backup_started" "${recovery_partial:-}"' EXIT
./backup.sh </dev/null >&2

backup_file=$(find "$PWD/backups" -maxdepth 1 -type f -name 'stoxsim-*.dump' -newer "$backup_started" -printf '%T@ %p\n' \
  | sort -nr | head -n 1 | cut -d' ' -f2-)
if [[ -z "$backup_file" || ! -f "$backup_file" ]]; then
  echo "The staging backup command did not produce a new dump file for this recovery run." >&2
  exit 1
fi
if [[ ! -f "${backup_file}.sha256" ]]; then
  echo "The staging backup command did not produce a checksum file." >&2
  exit 1
fi

backup_basename=$(basename "$backup_file")
if [[ ! "$backup_basename" =~ ^stoxsim-[0-9]{8}T[0-9]{6}Z\.dump$ ]]; then
  echo "The staging backup filename does not match the timestamped format." >&2
  exit 1
fi

echo "Verifying the timestamped staging backup"
(
  cd "$(dirname "$backup_file")"
  sha256sum --check "$(basename "${backup_file}.sha256")"
)

recovery_backup="$PWD/$TARGET_BACKUP"
recovery_partial="${recovery_backup}.partial"
umask 077
rm -f "$recovery_partial"
ln "$backup_file" "$recovery_partial"
mv -f "$recovery_partial" "$recovery_backup"
(
  cd "$(dirname "$recovery_backup")"
  sha256sum "$(basename "$recovery_backup")" > "$(basename "${recovery_backup}.sha256")"
  sha256sum --check "$(basename "${recovery_backup}.sha256")"
)

echo "Pinned and verified recovery backup: ${TARGET_BACKUP}"
