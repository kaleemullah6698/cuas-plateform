package pk.nust.cuas.reporting.domain.port;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import pk.nust.cuas.reporting.domain.model.ReportEvent;

/**
 * The event-projection read model (ARCHITECTURE.md §4.1.10): the consumers append projected domain
 * facts, and the report generators query them. This is reporting-service's own source of truth — it
 * never reads another service's database (CLAUDE.md §2). Elasticsearch is the scale-out mirror behind
 * the same shape; the ES-backed implementation is a documented follow-on.
 */
public interface ReportEventStore {

    /** Records a projected fact. Idempotent on the event's dedup key — a redelivery does not double-count. */
    void append(ReportEvent event);

    /** Every projected fact in a time range, ascending by occurrence. */
    List<ReportEvent> between(Instant from, Instant to);

    /** Projected facts of one event type in a range. */
    List<ReportEvent> ofTypeBetween(String eventType, Instant from, Instant to);

    /** Projected facts about one subject (an incident, an alert, a track). */
    List<ReportEvent> forSubject(UUID subjectId);
}
