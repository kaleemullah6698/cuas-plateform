package pk.nust.cuas.reporting.domain.model;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * One projected domain fact in the reporting read model (ARCHITECTURE.md §4.1.10) — the source the
 * report generators aggregate over.
 *
 * <p>Schemaless by design: {@code attributes} carries the event-type-specific facts (an alert's
 * disposition, a sensor's status, an inference's device) so a new fact does not require a schema
 * change. {@code dedupKey} makes projection idempotent — a topic redelivering an event does not
 * double-count it.
 *
 * @param eventType  the projected event, e.g. "alert.raised" or "sensor.health"
 * @param subjectId  the primary subject (alert, incident, track), if the event has one
 * @param occurredAt when the fact occurred, on the platform clock
 * @param attributes the event-type-specific facts
 * @param dedupKey   a stable identity for this fact, unique across redeliveries
 */
public record ReportEvent(String eventType, Optional<UUID> subjectId, Instant occurredAt,
                          Map<String, String> attributes, String dedupKey) {

    public ReportEvent {
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("eventType is required");
        }
        if (occurredAt == null) {
            throw new IllegalArgumentException("occurredAt is required");
        }
        if (dedupKey == null || dedupKey.isBlank()) {
            throw new IllegalArgumentException("dedupKey is required");
        }
        if (subjectId == null) {
            throw new IllegalArgumentException("use Optional.empty(), not null");
        }
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    /** @return the attribute, or empty if absent */
    public Optional<String> attribute(String key) {
        return Optional.ofNullable(attributes.get(key));
    }
}
