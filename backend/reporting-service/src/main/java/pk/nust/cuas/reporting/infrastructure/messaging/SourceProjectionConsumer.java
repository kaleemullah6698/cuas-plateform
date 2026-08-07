package pk.nust.cuas.reporting.infrastructure.messaging;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import pk.nust.cuas.alert.v1.Alert;
import pk.nust.cuas.alert.v1.AlertResolved;
import pk.nust.cuas.alert.v1.Incident;
import pk.nust.cuas.ingestion.v1.SensorHealth;
import pk.nust.cuas.inference.v1.InferenceResponse;
import pk.nust.cuas.reporting.domain.model.ProjectedEventTypes;
import pk.nust.cuas.reporting.domain.model.ReportEvent;
import pk.nust.cuas.reporting.domain.port.ReportEventStore;
import pk.nust.cuas.track.v1.Track;

/**
 * Projects the domain events reports are built from into the read model (ARCHITECTURE.md §4.1.10).
 *
 * <p>Reporting consumes rather than queries — database-per-service is absolute — so it keeps its own
 * projection of the facts it needs. Projection is idempotent on a per-event dedup key, so Kafka's
 * at-least-once redelivery never double-counts. A malformed record is acknowledged and skipped: it
 * will not parse on redelivery, and blocking the partition on one poison record would stall every
 * later projection (§5.6); routing to the DLQ is the M8 hardening item recorded for the other
 * consumers.
 */
@Component
public class SourceProjectionConsumer {

    private static final Logger log = LoggerFactory.getLogger(SourceProjectionConsumer.class);
    private static final String GROUP = "${spring.kafka.consumer.group-id:reporting-service}";

    private final ReportEventStore events;
    private final Clock clock;

    public SourceProjectionConsumer(ReportEventStore events, Clock clock) {
        this.events = events;
        this.clock = clock;
    }

    @KafkaListener(topics = ReportTopics.ALERT_RAISED, groupId = GROUP)
    public void onAlertRaised(ConsumerRecord<String, byte[]> record) {
        project(record, ReportTopics.ALERT_RAISED, bytes -> {
            Alert alert = Alert.parseFrom(bytes);
            return new ReportEvent(ProjectedEventTypes.ALERT_RAISED, uuid(alert.getAlertId()),
                    instant(alert.getRaisedAt()), attrs("severity", alert.getSeverity().name(),
                    "trackId", alert.getTrackId()), "alert.raised:" + alert.getAlertId());
        });
    }

    @KafkaListener(topics = ReportTopics.ALERT_RESOLVED, groupId = GROUP)
    public void onAlertResolved(ConsumerRecord<String, byte[]> record) {
        project(record, ReportTopics.ALERT_RESOLVED, bytes -> {
            AlertResolved resolved = AlertResolved.parseFrom(bytes);
            return new ReportEvent(ProjectedEventTypes.ALERT_RESOLVED, uuid(resolved.getAlertId()),
                    instant(resolved.getResolvedAt()), attrs("disposition", resolved.getDisposition().name(),
                    "trackId", resolved.getTrackId()), "alert.resolved:" + resolved.getAlertId());
        });
    }

    @KafkaListener(topics = ReportTopics.INCIDENT_CREATED, groupId = GROUP)
    public void onIncidentCreated(ConsumerRecord<String, byte[]> record) {
        project(record, ReportTopics.INCIDENT_CREATED, bytes -> {
            Incident incident = Incident.parseFrom(bytes);
            return new ReportEvent(ProjectedEventTypes.INCIDENT_CREATED, uuid(incident.getIncidentId()),
                    instant(incident.getOpenedAt()), attrs("severity", incident.getSeverity().name()),
                    "incident.created:" + incident.getIncidentId());
        });
    }

    @KafkaListener(topics = ReportTopics.INCIDENT_CLOSED, groupId = GROUP)
    public void onIncidentClosed(ConsumerRecord<String, byte[]> record) {
        project(record, ReportTopics.INCIDENT_CLOSED, bytes -> {
            Incident incident = Incident.parseFrom(bytes);
            return new ReportEvent(ProjectedEventTypes.INCIDENT_CLOSED, uuid(incident.getIncidentId()),
                    instant(incident.getClosedAt()), attrs("disposition",
                    incident.getClosureDisposition().name()), "incident.closed:" + incident.getIncidentId());
        });
    }

    @KafkaListener(topics = ReportTopics.TRACK_CREATED, groupId = GROUP)
    public void onTrackCreated(ConsumerRecord<String, byte[]> record) {
        project(record, ReportTopics.TRACK_CREATED, bytes -> {
            Track track = Track.parseFrom(bytes);
            return new ReportEvent(ProjectedEventTypes.TRACK_CREATED, uuid(track.getTrackId()),
                    instant(track.getFirstSeen()), Map.of(), "track.created:" + track.getTrackId());
        });
    }

    @KafkaListener(topics = ReportTopics.SENSOR_HEALTH, groupId = GROUP)
    public void onSensorHealth(ConsumerRecord<String, byte[]> record) {
        project(record, ReportTopics.SENSOR_HEALTH, bytes -> {
            SensorHealth health = SensorHealth.parseFrom(bytes);
            long lastSeen = health.hasLastSeen() ? health.getLastSeen().getSeconds() : 0L;
            Instant occurredAt = health.hasLastSeen() ? instant(health.getLastSeen()) : clock.instant();
            return new ReportEvent(ProjectedEventTypes.SENSOR_HEALTH, Optional.empty(), occurredAt,
                    attrs("sensorId", health.getSensorId(), "status", health.getStatus().name()),
                    "sensor.health:" + health.getSensorId() + ":" + lastSeen + ":" + health.getStatus());
        });
    }

    @KafkaListener(topics = ReportTopics.AI_INFERENCE_COMPLETED, groupId = GROUP)
    public void onInferenceCompleted(ConsumerRecord<String, byte[]> record) {
        project(record, ReportTopics.AI_INFERENCE_COMPLETED, bytes -> {
            InferenceResponse response = InferenceResponse.parseFrom(bytes);
            // InferenceResponse carries no timestamp; the observation time is when we projected it.
            return new ReportEvent(ProjectedEventTypes.AI_INFERENCE, uuid(response.getTrackId()),
                    clock.instant(), attrs("device", response.getDevice().name(),
                    "modelId", response.getModelId(), "trackId", response.getTrackId()),
                    "ai.inference:" + response.getRequestId());
        });
    }

    private void project(ConsumerRecord<String, byte[]> record, String topic, Projection projection) {
        try {
            events.append(projection.apply(record.value()));
        } catch (InvalidProtocolBufferException ex) {
            log.warn("Skipping an unparseable {} record at offset {}", topic, record.offset(), ex);
        }
    }

    private static Map<String, String> attrs(String... keyValues) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            if (keyValues[i + 1] != null && !keyValues[i + 1].isBlank()) {
                map.put(keyValues[i], keyValues[i + 1]);
            }
        }
        return map;
    }

    private static Optional<UUID> uuid(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private static Instant instant(Timestamp timestamp) {
        return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
    }

    @FunctionalInterface
    private interface Projection {
        ReportEvent apply(byte[] bytes) throws InvalidProtocolBufferException;
    }
}
