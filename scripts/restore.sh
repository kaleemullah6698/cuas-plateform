#!/bin/bash
#
# Platform restore — the other half of scripts/backup.sh (M12 reliability).
#
# A backup nobody has restored is a hypothesis, not a backup. This script is the tested path, and it
# ends by verifying the audit hash chain, because a restore that silently corrupted the one
# tamper-evident structure in the platform would be worse than no restore at all (§4.1.11, §7.5).
#
# Restores are **destructive by design**: each dump was taken with --clean --if-exists, so replaying
# it drops and recreates the objects it owns. The script therefore refuses to run without an explicit
# confirmation, and names what it is about to overwrite.
#
# Per-database restore is supported and is the common case — recovering one service must not require
# touching the other nine (§2, database-per-service):
#
#   bash scripts/restore.sh backups/20260813T090000Z              # everything
#   bash scripts/restore.sh backups/20260813T090000Z alertdb      # one database
#
set -uo pipefail

export MSYS_NO_PATHCONV=1
export MSYS2_ARG_CONV_EXCL='*'

cd "$(dirname "$0")/.."

if [ -f .env ]; then
  while IFS= read -r line || [ -n "$line" ]; do
    case "$line" in
      ''|'#'*) continue ;;
      *=*)
        key="${line%%=*}"
        if [ -z "$(eval "printf '%s' \"\${$key:-}\"")" ]; then export "$key=${line#*=}"; fi
        ;;
    esac
  done < .env
fi

PG_USER="${POSTGRES_USER:-cuas}"
SRC="${1:-}"
ONLY="${2:-}"

if [ -z "$SRC" ] || [ ! -d "$SRC" ]; then
  echo "usage: bash scripts/restore.sh <backup-directory> [database]"
  echo
  echo "available backups:"
  ls -1 backups 2>/dev/null | sed 's/^/  /' || echo "  (none under ./backups)"
  exit 1
fi

if [ ! -f "$SRC/MANIFEST" ]; then
  echo "[FATAL] $SRC has no MANIFEST — refusing to restore from an unidentified directory."
  exit 1
fi

echo "== CUAS platform restore =="
sed 's/^/   /' "$SRC/MANIFEST"
echo

if [ -n "$ONLY" ]; then
  TARGETS="$ONLY"
else
  TARGETS="$(cd "$SRC" && ls -1 ./*.sql.gz 2>/dev/null | sed 's|^\./||; s|\.sql\.gz$||' | tr '\n' ' ')"
fi

if [ -z "${TARGETS// /}" ]; then
  echo "[FATAL] no .sql.gz dumps found in $SRC"
  exit 1
fi

echo "This will DROP AND REPLACE the contents of: $TARGETS"
echo "The services using them should be stopped first:"
echo "  docker compose --profile platform stop"
echo
printf 'Type RESTORE to continue: '
read -r confirmation
if [ "$confirmation" != "RESTORE" ]; then
  echo "aborted."
  exit 1
fi

if ! docker compose ps postgres --format '{{.Status}}' 2>/dev/null | grep -q "Up"; then
  echo "[FATAL] postgres is not running."
  exit 1
fi

FAILED=0
for db in $TARGETS; do
  dump="$SRC/$db.sql.gz"
  printf '   %-10s ' "$db"
  if [ ! -f "$dump" ]; then
    echo "no dump in this backup"
    FAILED=$((FAILED + 1))
    continue
  fi
  # ON_ERROR_STOP so a mid-dump failure is a failure, not a half-restored database reported as fine.
  if gzip -dc "$dump" | docker compose exec -T postgres psql -v ON_ERROR_STOP=1 -U "$PG_USER" -d "$db" >/dev/null 2>&1; then
    echo "restored"
  else
    echo "FAILED"
    FAILED=$((FAILED + 1))
  fi
done

# ── Object store ────────────────────────────────────────────────────────────────────────────────
if [ -z "$ONLY" ] && [ -d "$SRC/minio" ]; then
  printf '   %-10s ' "minio"
  if docker compose cp "$SRC/minio/." minio:/data >/dev/null 2>&1; then
    echo "restored"
  else
    echo "FAILED"
    FAILED=$((FAILED + 1))
  fi
fi

echo
echo "== post-restore verification =="
echo "   Restart the platform, then verify the audit chain — a restored auditdb that fails"
echo "   verification means the backup is corrupt, and nothing else in the platform will say so:"
echo
echo "     docker compose --profile platform up -d"
echo "     bash scripts/verify-platform.sh"
echo
echo "   and, authenticated as a user holding audit:verify (§7.4):"
echo "     GET /api/v1/audit/verify   -> expect a valid chain, no first-broken-link"

echo
echo "======================================================="
if [ "$FAILED" -eq 0 ]; then
  echo " restore complete from $SRC"
else
  echo " restore finished with $FAILED failure(s)"
fi
echo "======================================================="
[ "$FAILED" -eq 0 ]
