#!/bin/bash
#
# Creates one database per service. Database-per-service is absolute
# (ARCHITECTURE.md section 4.1): no service reads another's tables.
#
# Run automatically by the postgres container on first initialisation
# (mounted into /docker-entrypoint-initdb.d). Idempotent: each database is
# created only if it does not already exist, so a re-run is harmless.
#
# The POSTGRES_USER (default "cuas") owns every database. Per-service least-
# privilege roles are introduced with the services that need them, not here.
#
set -euo pipefail

OWNER="${POSTGRES_USER:-cuas}"

# One database per service (ARCHITECTURE.md section 4.1 service inventory).
DATABASES=(
  authdb      # auth-service
  userdb      # user-service
  sensordb    # sensor-ingestion
  trackdb     # track-correlation
  threatdb    # threat-assessment
  alertdb     # alert-service
  notifydb    # notification-service
  reportdb    # reporting-service
  auditdb     # audit-service
  configdb    # config-service
)

create_database() {
  local db="$1"
  # gate on existence so the script is safe to re-run
  if psql -v ON_ERROR_STOP=1 --username "$OWNER" --dbname postgres -tAc \
       "SELECT 1 FROM pg_database WHERE datname = '${db}'" | grep -q 1; then
    echo "init-databases: '${db}' already exists, skipping"
  else
    echo "init-databases: creating '${db}'"
    psql -v ON_ERROR_STOP=1 --username "$OWNER" --dbname postgres \
      -c "CREATE DATABASE ${db} OWNER ${OWNER}"
  fi
}

for db in "${DATABASES[@]}"; do
  create_database "$db"
done

echo "init-databases: complete (${#DATABASES[@]} databases)"
