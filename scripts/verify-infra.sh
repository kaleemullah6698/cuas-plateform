#!/bin/bash
#
# Infrastructure acceptance test — closes the M0/M1 infra verification items.
#
# Brings up the infra profile, waits for every service to become healthy, applies the
# database and topic catalogues, and asserts that all ten databases and every Kafka topic in
# the ARCHITECTURE.md section 5.2 catalogue exists, and that Schema Registry, Redis, and MinIO
# respond. Idempotent and safe to re-run.
#
# Prerequisite: Docker Desktop running, with Compose v2 (`docker compose version`).
#
# Usage (from the repository root):
#   bash scripts/verify-infra.sh
#
set -uo pipefail

# On Windows/Git Bash (MSYS), a container-absolute path like /scripts/create-topics.sh is
# rewritten into a host path (C:/Program Files/Git/scripts/...) before it reaches the container.
# Disabling that conversion keeps `docker compose exec` arguments intact. No effect elsewhere.
export MSYS_NO_PATHCONV=1
export MSYS2_ARG_CONV_EXCL='*'

cd "$(dirname "$0")/.."   # repository root

PASS=0
FAIL=0
pass() { echo "  [PASS] $1"; PASS=$((PASS + 1)); }
fail() { echo "  [FAIL] $1"; FAIL=$((FAIL + 1)); }

dc() { docker compose "$@"; }

# Poll a readiness command until it succeeds or times out. Each service is probed with a tool
# guaranteed to exist for it (its own client, or host curl against a mapped port) rather than
# assuming a particular HTTP client inside every image.
wait_ready() {
  local name="$1" timeout="$2"; shift 2
  local waited=0
  printf 'waiting for %s ' "$name"
  while [ "$waited" -lt "$timeout" ]; do
    if "$@" >/dev/null 2>&1; then echo " ok"; return 0; fi
    printf '.'; sleep 3; waited=$((waited + 3))
  done
  echo " timeout"; return 1
}

check_postgres() { dc exec -T postgres pg_isready -U "${POSTGRES_USER:-cuas}"; }
check_kafka()    { dc exec -T kafka kafka-topics --bootstrap-server localhost:9092 --list; }
check_redis()    { dc exec -T redis sh -c 'redis-cli -a "$REDISCLI_AUTH" --no-auth-warning ping' | grep -q PONG; }
check_sr()       { curl -fsS http://localhost:8081/subjects; }
check_es()       { curl -fsS http://localhost:9200/_cluster/health; }
check_minio()    { curl -fsS http://localhost:9000/minio/health/live; }

echo "== 0. Docker Compose v2 =="
if docker compose version >/dev/null 2>&1; then
  pass "docker compose v2 present ($(docker compose version --short 2>/dev/null))"
else
  echo "  [FATAL] 'docker compose' v2 not available. Install Docker Desktop first."
  exit 1
fi

echo "== 1. Bring up infra profile =="
if dc --profile infra up -d; then
  pass "compose up (infra)"
else
  fail "compose up (infra)"; echo "aborting"; exit 1
fi

echo "== 2. Service readiness =="
wait_ready postgres      120 check_postgres && pass "postgres ready"           || fail "postgres ready"
wait_ready kafka         150 check_kafka    && pass "kafka ready"              || fail "kafka ready"
wait_ready schema-registry 120 check_sr     && pass "schema-registry ready"    || fail "schema-registry ready"
wait_ready elasticsearch 180 check_es       && pass "elasticsearch ready"      || fail "elasticsearch ready"
wait_ready redis         60  check_redis    && pass "redis ready"              || fail "redis ready"
wait_ready minio         90  check_minio    && pass "minio ready"              || fail "minio ready"

echo "== 3. Apply database catalogue (init runs on first init; re-assert here) =="
dc exec -T postgres bash /docker-entrypoint-initdb.d/init-databases.sh >/dev/null 2>&1 || true

echo "== 4. Databases exist =="
EXPECTED_DBS="authdb userdb sensordb trackdb threatdb alertdb notifydb reportdb auditdb configdb"
ACTUAL_DBS=$(dc exec -T postgres psql -U "${POSTGRES_USER:-cuas}" -tAc \
  "SELECT datname FROM pg_database" 2>/dev/null | tr -d '\r')
for db in $EXPECTED_DBS; do
  if echo "$ACTUAL_DBS" | grep -qx "$db"; then pass "database $db"; else fail "database $db"; fi
done

echo "== 5. Apply Kafka topic catalogue =="
if dc exec -T kafka bash /scripts/create-topics.sh >/dev/null 2>&1; then
  pass "create-topics.sh applied"
else
  fail "create-topics.sh applied"
fi

# 43 = 32 primary + 11 DLQ, as declared in create-topics.sh. Asserted as a floor rather than an
# equality: a milestone adds topics to the catalogue, and a >= check keeps this script from
# failing on the addition itself while still catching a topic that failed to create.
echo "== 6. Topics exist (expect 43: 32 primary + 11 DLQ) =="
ACTUAL_TOPICS=$(dc exec -T kafka kafka-topics --bootstrap-server localhost:9092 --list 2>/dev/null | tr -d '\r')
TOPIC_COUNT=$(echo "$ACTUAL_TOPICS" | grep -cE '\.v1$|\.dlq$' || true)
for t in sensor.detection.raw.v1 sensor.detection.normalized.v1 track.created.v1 \
         ai.inference.completed.v1 threat.level.changed.v1 \
         threat.recommendation.generated.v1 alert.raised.v1 alert.escalated.v1 \
         alert.resolved.v1 incident.updated.v1 incident.closed.v1 \
         notification.sent.v1 notification.failed.v1 \
         platform.audit.v1 platform.config.updated.v1 alert.raised.v1.dlq; do
  if echo "$ACTUAL_TOPICS" | grep -qx "$t"; then pass "topic $t"; else fail "topic $t"; fi
done
if [ "$TOPIC_COUNT" -ge 43 ]; then pass "topic count = $TOPIC_COUNT (>= 43)"; else fail "topic count = $TOPIC_COUNT (< 43)"; fi

echo "== 7. Schema Registry / Redis / MinIO reachable =="
check_sr    >/dev/null 2>&1 && pass "schema-registry /subjects"  || fail "schema-registry /subjects"
check_redis >/dev/null 2>&1 && pass "redis PING"                 || fail "redis PING"
check_minio >/dev/null 2>&1 && pass "minio health/live"          || fail "minio health/live"

echo "== 8. Elasticsearch cluster health =="
check_es >/dev/null 2>&1 && pass "elasticsearch cluster health"  || fail "elasticsearch cluster health"

echo
echo "==================================================="
echo " infra verification: ${PASS} passed, ${FAIL} failed"
echo "==================================================="
[ "$FAIL" -eq 0 ]
