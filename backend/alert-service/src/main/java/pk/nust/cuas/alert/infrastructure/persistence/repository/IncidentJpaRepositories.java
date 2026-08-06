package pk.nust.cuas.alert.infrastructure.persistence.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pk.nust.cuas.alert.infrastructure.persistence.entity.IncidentDetailEntities;
import pk.nust.cuas.alert.infrastructure.persistence.entity.IncidentEntity;

/** Spring Data repositories for the incident tables. */
public final class IncidentJpaRepositories {

    private IncidentJpaRepositories() {
    }

    /** {@code incidents}. */
    public interface IncidentJpa extends JpaRepository<IncidentEntity, UUID> {

        @Query("select i from IncidentEntity i "
                + "where (:status is null or i.status = :status) "
                + "order by i.openedAt desc")
        List<IncidentEntity> search(@Param("status") String status, Pageable page);

        /** Feeds the {@code INC-YYYY-NNNN} reference; counted rather than sequenced so a gap in the
         *  sequence cannot make a year's incidents look under-reported. */
        @Query("select count(i) from IncidentEntity i "
                + "where i.openedAt >= :from and i.openedAt < :to")
        long countOpenedBetween(@Param("from") Instant from, @Param("to") Instant to);
    }

    /** {@code incident_events}. */
    public interface IncidentEventJpa
            extends JpaRepository<IncidentDetailEntities.EventEntity, UUID> {

        List<IncidentDetailEntities.EventEntity> findByIncidentIdOrderByOccurredAtAsc(
                UUID incidentId);
    }

    /** {@code incident_notes}. */
    public interface IncidentNoteJpa
            extends JpaRepository<IncidentDetailEntities.NoteEntity, UUID> {

        List<IncidentDetailEntities.NoteEntity> findByIncidentIdOrderByCreatedAtAsc(UUID incidentId);
    }

    /** {@code incident_evidence}. */
    public interface IncidentEvidenceJpa
            extends JpaRepository<IncidentDetailEntities.EvidenceEntity, UUID> {

        List<IncidentDetailEntities.EvidenceEntity> findByIncidentIdOrderByCapturedAtAsc(
                UUID incidentId);
    }
}
