package pk.nust.cuas.reporting.infrastructure.persistence.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pk.nust.cuas.reporting.infrastructure.persistence.entity.ReportEventEntity;

/** Spring Data repository for {@code report_events} — the projection read model. */
public interface ReportEventJpaRepository extends JpaRepository<ReportEventEntity, Long> {

    List<ReportEventEntity> findByOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAtAsc(
            Instant from, Instant to);

    List<ReportEventEntity>
            findByEventTypeAndOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAtAsc(
            String eventType, Instant from, Instant to);

    List<ReportEventEntity> findBySubjectIdOrderByOccurredAtAsc(UUID subjectId);

    boolean existsByDedupKey(String dedupKey);
}
