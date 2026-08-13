#!/bin/bash
#
# M11 live acceptance — the operator journey, in a real browser, against the assembled platform.
#
# scripts/verify-platform.sh proves the platform answers. This proves an operator can *use* it: it
# drives the built SPA the `web` container serves, with no mocked login and no mocked WebSocket, and
# replays a real intrusion while the browser is watching.
#
# The ordering matters and is the reason this is a script rather than two commands. The gateway's
# fan-out consumer reads from `latest` and its track snapshot is built from an in-memory view
# (ARCHITECTURE.md section 3.5, decision D-1). Anything replayed before the browser's WebSocket is
# established is therefore invisible to it — not lost, but never pushed. So the replay is fired
# *after* the browser has signed in and subscribed, not before.
#
# Prerequisites: the infra + platform profiles up and ready (scripts/verify-platform.sh sections 0-3),
# and a .env carrying CUAS_BOOTSTRAP_ADMIN_ENABLED=true and CUAS_BOOTSTRAP_ADMIN_PASSWORD.
#
# Usage (from the repository root):
#   bash scripts/verify-journey.sh
#
set -uo pipefail

export MSYS_NO_PATHCONV=1
export MSYS2_ARG_CONV_EXCL='*'

cd "$(dirname "$0")/.."

# Same .env handling as verify-platform.sh: existing environment values win.
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

if [ -z "${CUAS_BOOTSTRAP_ADMIN_PASSWORD:-}" ]; then
  echo "[FATAL] CUAS_BOOTSTRAP_ADMIN_PASSWORD is not set (see .env)."
  exit 1
fi

# How long the browser needs to reach /tracks with a live socket before the replay is worth firing.
REPLAY_DELAY="${REPLAY_DELAY:-35}"

# Build the simulator image *before* the browser starts, not as part of the replay.
#
# `compose run` reuses whatever image already exists and does not rebuild on a source change, which
# cost a full debugging cycle: the run label had no effect, the sensor ids were still the old fixed
# ones, and every replay was dropped as duplicate — while the source on disk plainly had the fix.
# Building it inside the timed replay is not the answer either: the build then eats the window the
# spec is waiting in, and the replay lands after the assertions have already given up. So it happens
# here, once, with the clock not yet running. Both profiles, for the same depends_on reason as below.
echo "== building the simulator image =="
build_log="${TMPDIR:-/tmp}/cuas-sim-build.$$.log"
if docker compose --profile sim --profile platform build sensor-simulator >"$build_log" 2>&1; then
  echo "  sensor-simulator image current"
else
  # Not fatal, and the output is shown rather than swallowed: an existing image may still be current,
  # and a build that fails silently here looks identical to a replay that produced nothing.
  echo "  [WARN] build returned non-zero; continuing with the existing image. Tail:"
  tail -15 "$build_log" | sed 's/^/    /'
fi
rm -f "$build_log"

# This run spends exactly one sign-in, deliberately.
#
# RateLimitPolicy.LOGIN_PER_ACCOUNT is 10/hour with burst 3 (section 7.8), so the bucket refills one
# token roughly every six minutes. An earlier version of this script probed the login endpoint until
# it saw room — which was self-defeating, because the probe is itself a login and consumed the very
# token the browser then needed. There is no way to read the bucket without spending from it.
#
# So: no probe. If the browser's sign-in meets a 429 the spec says so explicitly, with the server's
# Retry-After, and the answer is to wait rather than to loosen the limit. A 429 here is the control
# working correctly, and it is a production limit, not a knob for a demo.

echo "== replaying the intrusion in ${REPLAY_DELAY}s, once the browser is subscribed =="
(
  sleep "$REPLAY_DELAY"
  echo "--- sensor-simulator: replay starting ---"
  # Repeated because a single run is a burst: track-correlation needs successive detections on the
  # same target to promote a tentative track to CONFIRMED, and the second run also covers the case
  # where the browser was still authenticating through the first.
  # The simulator's own defaults: 3 sensors x 100 detections over both transports.
  #
  # A smaller scenario was tried and produces no tracks at all — track-correlation needs successive
  # sweeps on the same target before it promotes one (gate-m plus required-consecutive-sweeps), so
  # thinning the replay thins it below the confirmation threshold. The full burst is what actually
  # fills the console.
  #
  # It has a known consequence, which is a property of the mock and not a fault. The mock inference
  # provider samples a lognormal fitted so its p95 lands exactly on each service's configured
  # timeout (section 6.4) — deliberately, so the circuit breaker is exercised rather than never
  # tripped — and it sleeps for the sample. 600 detections in a few seconds therefore push
  # track-correlation past its PT0.5S Infer budget, its breaker opens, and tracks stay unclassified
  # while it is open: section 6.5's specified degradation, the pipeline continuing without the model
  # rather than stalling on it. Tracks, scores and alerts are unaffected; only the class label is.
  #
  # Both knobs are honoured if exported, so a run can be tuned without editing this script.
  export SIMULATOR_DETECTIONS_PER_SENSOR="${SIMULATOR_DETECTIONS_PER_SENSOR:-100}"
  export SIMULATOR_TRANSPORT="${SIMULATOR_TRANSPORT:-BOTH}"
  # A label unique to this script invocation, and distinct per replay within it. The intake dedups
  # on (sensorId, sensorSeq), so without this the second replay — and every replay in every later
  # run — is discarded in full as a redelivery of the first.
  RUN_STAMP="$$"
  for run in 1 2; do
    export SIMULATOR_RUN_LABEL="${RUN_STAMP}x${run}"
    # Both profiles: sensor-simulator depends_on sensor-ingestion, which lives in `platform`. With
    # only `sim` active that service is not part of the project at all and compose refuses the whole
    # file — "depends on undefined service sensor-ingestion: invalid compose project" — rather than
    # just skipping the dependency. --no-deps then keeps it from restarting the running stack.
    docker compose --profile sim --profile platform run --rm --no-deps sensor-simulator >/dev/null 2>&1 \
      && echo "--- sensor-simulator: replay $run complete ---" \
      || echo "--- sensor-simulator: replay $run FAILED ---"
  done
) &
REPLAY_PID=$!

echo "== driving the real console in Chromium =="
npm --prefix frontend run test:e2e:live
STATUS=$?

wait "$REPLAY_PID" 2>/dev/null

echo
echo "======================================================="
if [ "$STATUS" -eq 0 ]; then
  echo " live operator journey: PASSED"
else
  echo " live operator journey: FAILED (exit $STATUS)"
fi
echo "======================================================="
exit "$STATUS"
