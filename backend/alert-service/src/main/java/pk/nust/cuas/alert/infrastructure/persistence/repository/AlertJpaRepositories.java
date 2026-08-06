package pk.nust.cuas.alert.infrastructure.persistence.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pk.nust.cuas.alert.infrastructure.persistence.entity.AlertEntity;
import pk.nust.cuas.alert.infrastructure.persistence.entity.AlertHistoryEntities;

/** Spring Data repositories for the alert tables. */
public final class AlertJpaRepositories {

    private AlertJpaRepositories() {
    }

    /** {@code alerts}. */
    public interface AlertJpa extends JpaRepository<AlertEntity, UUID> {

        /**
         * The open alert with a given deduplication identity. Mirrors {@code uq_alert_open_dedup},
         * so the read and the constraint agree about what "open" means.
         */
        @Query("select a from AlertEntity a where a.dedupKey = :key "
                + "and a.status not in ('RESOLVED', 'SUPPRESSED')")
        Optional<AlertEntity> findOpenByDedupKey(@Param("key") String dedupKey);

        @Query("select a from AlertEntity a where a.trackId = :trackId "
                + "and a.status not in ('RESOLVED', 'SUPPRESSED')")
        List<AlertEntity> findOpenByTrackId(@Param("trackId") UUID trackId);

        /**
         * The database half of the escalation sweep. Restricted to escalatable statuses so a
         * resolved alert with a stale deadline can never be resurrected by it.
         */
        @Query("select a from AlertEntity a where a.escalatesAt is not null "
                + "and a.escalatesAt <= :deadline and a.status in ('RAISED', 'ESCALATED') "
                + "order by a.escalatesAt asc")
        List<AlertEntity> findEscalationsDue(@Param("deadline") Instant deadline, Pageable page);

        /**
         * The read API's list. Every filter is optional and matched with a null guard rather than a
         * Specification: three optional predicates do not justify a criteria builder, and the JPQL
         * is readable by anybody who has to change it.
         */
        @Query("select a from AlertEntity a "
                + "where (:status is null or a.status = :status) "
                + "and (:severity is null or a.severity = :severity) "
                + "and (:sectorId is null or a.sectorId = :sectorId) "
                + "order by a.raisedAt desc")
        List<AlertEntity> search(@Param("status") String status,
                                 @Param("severity") String severity,
                                 @Param("sectorId") String sectorId,
                                 Pageable page);
    }

    /** {@code alert_acknowledgements}. */
    public interface AcknowledgementJpa
            extends JpaRepository<AlertHistoryEntities.AcknowledgementEntity, Long> {

        List<AlertHistoryEntities.AcknowledgementEntity> findByAlertIdOrderByOccurredAtAsc(
                UUID alertId);
    }

    /** {@code alert_escalations}. */
    public interface EscalationJpa
            extends JpaRepository<AlertHistoryEntities.EscalationEntity, Long> {

        List<AlertHistoryEntities.EscalationEntity> findByAlertIdOrderByOccurredAtAsc(UUID alertId);
    }

    /** {@code alert_resolutions}. */
    public interface ResolutionJpa
            extends JpaRepository<AlertHistoryEntities.ResolutionEntity, Long> {

        List<AlertHistoryEntities.ResolutionEntity> findByAlertId(UUID alertId);
    }
}
