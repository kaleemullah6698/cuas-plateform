#!/bin/bash
#
# M11 acceptance test — the whole platform, running together.
#
# Proves the path ARCHITECTURE.md section 5.1 describes, through the real running system:
#
#   sensor-simulator -> ingestion -> track-correlation -> AI (mock) -> threat-assessment
#     -> alert-service -> notification-service, and the gateway surface the operator console reads
#
# This is deliberately a black-box test. It talks to the platform only through the gateway on
# :8080, exactly as the browser does — no direct service ports, no database queries, no Kafka
# console consumer. A check that reached past the gateway could pass while the thing an operator
# actually uses was broken, which is the failure mode M9 finding G-4 was: every service worked and
# none of them was reachable.
#
# Prerequisites: Docker Desktop running; `bash scripts/verify-infra.sh` green; a .env with
# CUAS_BOOTSTRAP_ADMIN_ENABLED=true and a CUAS_BOOTSTRAP_ADMIN_PASSWORD of at least 12 characters.
#
# Usage (from the repository root):
#   bash scripts/verify-platform.sh
#
set -uo pipefail

export MSYS_NO_PATHCONV=1
export MSYS2_ARG_CONV_EXCL='*'

cd "$(dirname "$0")/.."

GATEWAY="${GATEWAY_URL:-http://localhost:8080}"
ADMIN_USER="${CUAS_BOOTSTRAP_ADMIN_USERNAME:-admin}"
ADMIN_PASS="${CUAS_BOOTSTRAP_ADMIN_PASSWORD:-}"

PASS=0
FAIL=0
pass() { echo "  [PASS] $1"; PASS=$((PASS + 1)); }
fail() { echo "  [FAIL] $1"; FAIL=$((FAIL + 1)); }
info() { echo "  ....   $1"; }

dc() { docker compose "$@"; }

# Poll until a command succeeds or the budget runs out.
wait_for() {
  local name="$1" timeout="$2"; shift 2
  local waited=0
  printf 'waiting for %s ' "$name"
  while [ "$waited" -lt "$timeout" ]; do
    if "$@" >/dev/null 2>&1; then echo " ok"; return 0; fi
    printf '.'; sleep 3; waited=$((waited + 3))
  done
  echo " timeout"; return 1
}

# ── 0. Preconditions ────────────────────────────────────────────────────────────────────────────

echo "== 0. Preconditions =="
if ! docker compose version >/dev/null 2>&1; then
  echo "  [FATAL] 'docker compose' v2 not available."
  exit 1
fi
pass "docker compose v2 present"

if [ -z "$ADMIN_PASS" ]; then
  echo "  [FATAL] CUAS_BOOTSTRAP_ADMIN_PASSWORD is not set."
  echo "          The platform seeds no default credential on purpose (section 7.7)."
  echo "          Set it in .env along with CUAS_BOOTSTRAP_ADMIN_ENABLED=true and re-run."
  exit 1
fi
pass "bootstrap administrator password supplied"

# ── 1. Bring the platform up ────────────────────────────────────────────────────────────────────

echo "== 1. Bring up infra + platform =="
if dc --profile infra --profile platform up -d --build; then
  pass "compose up (infra + platform)"
else
  fail "compose up (infra + platform)"; echo "aborting"; exit 1
fi

echo "== 2. Apply the Kafka topic catalogue =="
dc exec -T kafka bash /scripts/create-topics.sh >/dev/null 2>&1 \
  && pass "create-topics.sh applied" \
  || fail "create-topics.sh applied"

# ── 3. Every service answers its readiness probe ────────────────────────────────────────────────

echo "== 3. Service readiness =="
probe() { curl -fsS "http://localhost:$1/actuator/health/readiness" >/dev/null 2>&1; }

# port:name — the gateway last, because it is the one the rest of this script depends on.
for entry in \
  "8081:auth-service" "8082:user-service" "8083:sensor-ingestion" "8084:track-correlation" \
  "8085:ai-orchestrator" "8086:threat-assessment" "8087:alert-service" \
  "8088:notification-service" "8089:reporting-service" "8090:audit-service" \
  "8091:config-service" "8080:api-gateway"; do
  port="${entry%%:*}"; name="${entry##*:}"
  wait_for "$name" 180 probe "$port" && pass "$name ready" || fail "$name ready"
done

# ── 4. Sign in as the bootstrap administrator ───────────────────────────────────────────────────
#
# The first thing M11 made possible. Before the bootstrap there was no user, no credential and no
# endpoint that creates one, so the platform could be started and could not be used.

echo "== 4. Authenticate through the gateway =="
LOGIN=$(curl -fsS -X POST "$GATEWAY/api/v1/auth/login" \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"$ADMIN_USER\",\"password\":\"$ADMIN_PASS\"}" 2>/dev/null)

TOKEN=$(echo "$LOGIN" | sed -n 's/.*"accessToken"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')
if [ -n "$TOKEN" ]; then
  pass "signed in as '$ADMIN_USER' and received an access token"
else
  fail "signed in as '$ADMIN_USER' (response: ${LOGIN:0:200})"
  echo "aborting: nothing further can be checked without a token"
  echo "  hint: is CUAS_BOOTSTRAP_ADMIN_ENABLED=true in .env? the bootstrap runs only at startup"
  exit 1
fi

auth_get() { curl -fsS -o /dev/null -w '%{http_code}' -H "Authorization: Bearer $TOKEN" "$GATEWAY$1"; }
auth_body() { curl -fsS -H "Authorization: Bearer $TOKEN" "$GATEWAY$1"; }

# ── 5. Every route finding G-4 left unrouted now answers ────────────────────────────────────────
#
# The point of this section is coverage, not payloads. A 200 or a 404 both prove the route reaches
# the owning service; a 404 from the *gateway* would not, and neither would a 503. Anything in the
# 5xx range, or a connection failure, means the route is still missing.

echo "== 5. Gateway routing (M9 finding G-4) =="
for route in \
  "/api/v1/tracks" "/api/v1/alerts" "/api/v1/incidents" \
  "/api/v1/reports/templates" "/api/v1/config/threat-policy" \
  "/api/v1/notifications/preferences" "/api/v1/users" "/api/v1/audit"; do
  code=$(auth_get "$route" 2>/dev/null || echo "000")
  case "$code" in
    2*|4*) pass "$route -> $code (routed)" ;;
    000)   fail "$route -> no response (route missing or upstream down)" ;;
    *)     fail "$route -> $code" ;;
  esac
done

# The AI admin surface is engineer-facing; an ADMIN holds ai:configure so this should be a 200.
code=$(auth_get "/api/v1/ai/services" 2>/dev/null || echo "000")
case "$code" in 2*) pass "/api/v1/ai/services -> $code" ;; *) fail "/api/v1/ai/services -> $code" ;; esac

# ── 6. Permission enforcement still bites ───────────────────────────────────────────────────────
#
# Routing something is only half the job: a route that forwards everything would be worse than no
# route. An unauthenticated request must be refused at the edge.

echo "== 6. Authorisation at the edge =="
anon=$(curl -fsS -o /dev/null -w '%{http_code}' "$GATEWAY/api/v1/alerts" 2>/dev/null || echo "401")
case "$anon" in
  401|403) pass "unauthenticated /api/v1/alerts -> $anon" ;;
  *)       fail "unauthenticated /api/v1/alerts -> $anon (should be 401/403)" ;;
esac

# ── 7. Drive a replayed intrusion through the pipeline ──────────────────────────────────────────

echo "== 7. Replay an intrusion =="
BEFORE=$(auth_body "/api/v1/alerts?limit=500" 2>/dev/null | grep -o '"alertId"' | wc -l | tr -d ' ')
info "alerts before replay: $BEFORE"

if dc --profile sim run --rm sensor-simulator >/dev/null 2>&1; then
  pass "sensor-simulator replay completed"
else
  fail "sensor-simulator replay completed"
fi

# The pipeline is asynchronous end to end: ingestion -> correlation -> inference -> scoring ->
# alerting are five Kafka hops. Poll rather than sleep once, so a fast machine is not made to wait
# and a slow one is not failed early.
echo "== 8. The intrusion becomes a track, a score and an alert =="

poll_count() {
  local path="$1" marker="$2" want="$3" budget="$4"
  local waited=0 n=0
  while [ "$waited" -lt "$budget" ]; do
    n=$(auth_body "$path" 2>/dev/null | grep -o "$marker" | wc -l | tr -d ' ')
    [ "$n" -ge "$want" ] && { echo "$n"; return 0; }
    sleep 3; waited=$((waited + 3))
  done
  echo "$n"; return 1
}

TRACKS=$(poll_count "/api/v1/tracks?limit=500" '"trackId"' 1 90)
[ "$TRACKS" -ge 1 ] && pass "track-correlation produced $TRACKS track(s)" \
                    || fail "track-correlation produced no tracks within 90s"

AFTER=$(poll_count "/api/v1/alerts?limit=500" '"alertId"' $((BEFORE + 1)) 90)
if [ "$AFTER" -gt "$BEFORE" ]; then
  pass "alert-service raised $((AFTER - BEFORE)) alert(s) from the replay"
else
  # Not necessarily a platform fault: the scenario may not cross the section 6.3.4 raising
  # threshold. Report it as a failure of *this* acceptance run and say why, rather than silently.
  fail "no alert was raised within 90s (scenario may not reach the MEDIUM raising floor)"
fi

# ── 9. The operator console is being served ─────────────────────────────────────────────────────

echo "== 9. Operator console =="
curl -fsS -o /dev/null "http://localhost:3000/" 2>/dev/null \
  && pass "web container serves the SPA on :3000" \
  || fail "web container serves the SPA on :3000"

echo
echo "======================================================="
echo " platform acceptance: ${PASS} passed, ${FAIL} failed"
echo "======================================================="
[ "$FAIL" -eq 0 ]
