#!/bin/bash
#
# Declares the Kafka topic catalogue (ARCHITECTURE.md section 5.2).
#
# Topic auto-creation is disabled on the broker by design, so every topic a producer or consumer
# uses must be declared here. Idempotent: uses --if-not-exists, so re-running is harmless and the
# script can serve as a kafka-init container command or a manual bootstrap step.
#
# Invocation:
#   docker compose exec kafka bash /scripts/create-topics.sh
# or from inside the kafka container with kafka-topics on PATH.
#
# Naming: <domain>.<entity>.<event>.v<major>. A breaking schema change creates a new topic
# (….v2) that runs in parallel with the old during migration (section 5.5).
#
set -euo pipefail

BOOTSTRAP="${KAFKA_BOOTSTRAP_SERVERS:-kafka:9092}"
REPLICATION="${KAFKA_REPLICATION_FACTOR:-1}"     # single broker in dev; raise in production
TOPICS_CMD="${KAFKA_TOPICS_CMD:-kafka-topics}"

# Retention constants (milliseconds). -1 is infinite retention.
readonly R_24H=86400000
readonly R_7D=604800000
readonly R_30D=2592000000
readonly R_90D=7776000000
readonly R_INF=-1

# Primary catalogue, one row per topic (section 5.2):
#   name partitions retention_ms cleanup_policy
PRIMARY_TOPICS=(
  "sensor.detection.raw.v1          12 ${R_24H} delete"
  "sensor.detection.normalized.v1   12 ${R_7D}  delete"
  "sensor.health.v1                  3 ${R_7D}  delete"
  "sensor.ingestion.error.v1         3 ${R_30D} delete"
  "track.created.v1                  6 ${R_30D} delete"
  "track.updated.v1                  6 ${R_7D}  delete"
  "track.lost.v1                     6 ${R_30D} delete"
  "track.state.snapshot.v1           6 ${R_INF} compact"
  "ai.inference.requested.v1         6 ${R_24H} delete"
  "ai.inference.completed.v1         6 ${R_7D}  delete"
  "ai.inference.failed.v1            3 ${R_30D} delete"
  "threat.assessed.v1                6 ${R_30D} delete"
  "threat.level.changed.v1           6 ${R_90D} delete"
  "alert.raised.v1                   3 ${R_90D} delete"
  "alert.acknowledged.v1             3 ${R_90D} delete"
  "incident.created.v1               3 ${R_INF} delete"
  "platform.config.updated.v1        1 ${R_INF} compact"
  "platform.config.sensor.updated.v1 1 ${R_INF} compact"
  "platform.audit.v1                 3 ${R_INF} delete"
  "platform.user.deactivated.v1      3 ${R_INF} delete"
  "platform.gateway.access.v1        3 ${R_7D}  delete"
  "platform.gateway.ratelimit.v1     3 ${R_30D} delete"
)

# Dead-letter queues (section 5.2, `*.DLQ` row: 3 partitions, 30 d, delete). One DLQ is
# provisioned for each consumed pipeline topic — the hops where a poison message must be
# quarantined without blocking the stream (section 5.6).
DLQ_SOURCES=(
  "sensor.detection.normalized.v1"
  "track.created.v1"
  "track.updated.v1"
  "ai.inference.completed.v1"
  "threat.level.changed.v1"
  "alert.raised.v1"
)

create_topic() {
  local name="$1" partitions="$2" retention_ms="$3" cleanup="$4"
  echo "create-topics: ${name} (partitions=${partitions}, retention.ms=${retention_ms}, cleanup=${cleanup})"
  "${TOPICS_CMD}" --bootstrap-server "${BOOTSTRAP}" \
    --create --if-not-exists \
    --topic "${name}" \
    --partitions "${partitions}" \
    --replication-factor "${REPLICATION}" \
    --config "cleanup.policy=${cleanup}" \
    --config "retention.ms=${retention_ms}"
}

echo "create-topics: bootstrap=${BOOTSTRAP} replication=${REPLICATION}"

for row in "${PRIMARY_TOPICS[@]}"; do
  # shellcheck disable=SC2086 -- intentional word splitting of the fixed-shape row
  set -- ${row}
  create_topic "$1" "$2" "$3" "$4"
done

for source in "${DLQ_SOURCES[@]}"; do
  create_topic "${source}.dlq" 3 "${R_30D}" delete
done

echo "create-topics: complete (${#PRIMARY_TOPICS[@]} primary + ${#DLQ_SOURCES[@]} DLQ topics)"
