#!/bin/bash
#
# Platform backup — ARCHITECTURE.md §8, M12 reliability.
#
# Captures the state that cannot be rebuilt from source: the ten per-service databases and the
# MinIO object store that holds generated report artifacts (§4.1.10). Everything else in the topology
# is derivable — images come from the Dockerfiles, topics from `create-topics.sh`, and Kafka's log is
# a transport, not a system of record.
#
# **Database-per-service is preserved in the backup.** Each database is dumped to its own file rather
# than into one cluster-wide archive, because a restore that can only proceed all-or-nothing would
# make it impossible to recover one service without touching the other nine — and §2's absolute rule
# is that no service reads another's tables. A per-database dump keeps that true through recovery.
#
# The audit database deserves a note. Its hash chain (§4.1.11) is verifiable after restore precisely
# because pg_dump preserves row content and order-independent hashes; a restored auditdb that fails
# `POST /api/v1/audit/verify` means the backup is corrupt, and that check is part of restore.sh.
#
# Usage (from the repository root, with the stack running):
#   bash scripts/backup.sh [destination-directory]
#
# Produces <destination>/<UTC timestamp>/ containing one .sql.gz per database, the MinIO tree, and a
# MANIFEST recording what was taken and from where.
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
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
DEST_ROOT="${1:-backups}"
DEST="$DEST_ROOT/$STAMP"

# Every database the platform owns (infrastructure/postgres/init-databases.sh).
DATABASES="authdb userdb sensordb trackdb threatdb alertdb notifydb reportdb auditdb configdb"

FAILED=0

echo "== CUAS platform backup =="
echo "   destination: $DEST"

if ! docker compose ps postgres --format '{{.Status}}' 2>/dev/null | grep -q "Up"; then
  echo "  [FATAL] postgres is not running; start the stack before backing up."
  exit 1
fi

mkdir -p "$DEST"

# ── Databases ───────────────────────────────────────────────────────────────────────────────────
echo "== dumping databases =="
for db in $DATABASES; do
  printf '   %-10s ' "$db"
  # --clean --if-exists so the dump is replayable over an existing database; no --create, because
  # the databases are created by the postgres init script and owned by the topology, not the dump.
  if docker compose exec -T postgres pg_dump -U "$PG_USER" --clean --if-exists "$db" 2>/dev/null \
       | gzip > "$DEST/$db.sql.gz"; then
    size=$(wc -c < "$DEST/$db.sql.gz" | tr -d ' ')
    # A dump smaller than a gzip header plus a comment is an empty file wearing a .gz suffix.
    if [ "$size" -lt 100 ]; then
      echo "EMPTY ($size bytes) — treating as a failure"
      FAILED=$((FAILED + 1))
    else
      echo "ok ($size bytes)"
    fi
  else
    echo "FAILED"
    FAILED=$((FAILED + 1))
  fi
done

# ── Object store ────────────────────────────────────────────────────────────────────────────────
# Report artifacts are filesystem-stored with a sha256 (§4.1.10); losing them loses the evidence
# bundles an incident investigation exported.
echo "== copying the object store =="
if docker compose ps minio --format '{{.Status}}' 2>/dev/null | grep -q "Up"; then
  if docker compose cp minio:/data "$DEST/minio" >/dev/null 2>&1; then
    echo "   minio     ok"
  else
    echo "   minio     FAILED"
    FAILED=$((FAILED + 1))
  fi
else
  echo "   minio     skipped (not running)"
fi

# ── Manifest ────────────────────────────────────────────────────────────────────────────────────
{
  echo "cuas-platform backup"
  echo "taken-at:    $STAMP"
  echo "host:        $(hostname 2>/dev/null || echo unknown)"
  echo "git-commit:  $(git rev-parse HEAD 2>/dev/null || echo unknown)"
  echo "postgres-user: $PG_USER"
  echo "databases:   $DATABASES"
  echo
  echo "restore with: bash scripts/restore.sh $DEST"
} > "$DEST/MANIFEST"

echo
echo "======================================================="
if [ "$FAILED" -eq 0 ]; then
  echo " backup complete: $DEST"
else
  echo " backup finished with $FAILED failure(s) — do NOT rely on it"
fi
echo "======================================================="
[ "$FAILED" -eq 0 ]
