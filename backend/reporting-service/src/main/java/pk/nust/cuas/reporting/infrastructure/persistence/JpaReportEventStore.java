package pk.nust.cuas.reporting.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import pk.nust.cuas.reporting.domain.model.ReportEvent;
import pk.nust.cuas.reporting.domain.port.ReportEventStore;
import pk.nust.cuas.reporting.infrastructure.persistence.entity.ReportEventEntity;
import pk.nust.cuas.reporting.infrastructure.persistence.repository.ReportEventJpaRepository;

/**
 * JPA implementation of {@link ReportEventStore} over {@code reportdb}.
 *
 * <p>Appending is idempotent on the dedup key: a Kafka topic redelivers, and a projection that counted
 * a redelivered event twice would corrupt every report over it. The existence check handles the common
 * case; the unique constraint is the backstop, and losing that race is caught and ignored — the single
 * {@code save} runs in its own transaction, so the caught constraint violation has no surrounding
 * transaction to poison.
 */
@Repository
public class JpaReportEventStore implements ReportEventStore {

    private static final TypeReference<Map<String, String>> ATTRIBUTES = new TypeReference<>() {
    };

    private final ReportEventJpaRepository events;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public JpaReportEventStore(ReportEventJpaRepository events, ObjectMapper objectMapper, Clock clock) {
        this.events = events;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public void append(ReportEvent event) {
        if (events.existsByDedupKey(event.dedupKey())) {
            return;
        }
        try {
            events.save(toEntity(event));
        } catch (DataIntegrityViolationException ex) {
            // A concurrent consumer projected the same event; the row is already there.
        }
    }

    @Override
    public List<ReportEvent> between(Instant from, Instant to) {
        return events
                .findByOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAtAsc(from, to)
                .stream().map(this::toDomain).toList();
    }

    @Override
    public List<ReportEvent> ofTypeBetween(String eventType, Instant from, Instant to) {
        return events
                .findByEventTypeAndOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAtAsc(
                        eventType, from, to)
                .stream().map(this::toDomain).toList();
    }

    @Override
    public List<ReportEvent> forSubject(UUID subjectId) {
        return events.findBySubjectIdOrderByOccurredAtAsc(subjectId).stream()
                .map(this::toDomain).toList();
    }

    private ReportEventEntity toEntity(ReportEvent event) {
        LocalDate day = LocalDate.ofInstant(event.occurredAt(), ZoneOffset.UTC);
        return new ReportEventEntity(event.eventType(), event.subjectId().orElse(null),
                event.occurredAt(), day, writeAttributes(event.attributes()), event.dedupKey(),
                clock.instant());
    }

    private ReportEvent toDomain(ReportEventEntity entity) {
        return new ReportEvent(entity.eventType(), Optional.ofNullable(entity.subjectId()),
                entity.occurredAt(), readAttributes(entity.attributes()), entity.dedupKey());
    }

    private String writeAttributes(Map<String, String> attributes) {
        try {
            return objectMapper.writeValueAsString(attributes);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("could not serialise projection attributes", ex);
        }
    }

    private Map<String, String> readAttributes(String json) {
        try {
            return objectMapper.readValue(json, ATTRIBUTES);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("stored projection attributes are not valid JSON", ex);
        }
    }
}
