package pk.nust.cuas.alert.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import pk.nust.cuas.alert.domain.model.Acknowledgement;
import pk.nust.cuas.alert.domain.model.Alert;
import pk.nust.cuas.alert.domain.model.AlertDisposition;
import pk.nust.cuas.alert.domain.model.AlertStatus;
import pk.nust.cuas.alert.domain.model.DedupKey;
import pk.nust.cuas.alert.domain.model.Escalation;
import pk.nust.cuas.alert.domain.model.EscalationTrigger;
import pk.nust.cuas.alert.domain.model.Recommendation;
import pk.nust.cuas.alert.domain.model.Resolution;
import pk.nust.cuas.alert.domain.model.Severity;
import pk.nust.cuas.alert.domain.port.AlertRepository;
import pk.nust.cuas.alert.infrastructure.persistence.entity.AlertEntity;
import pk.nust.cuas.alert.infrastructure.persistence.entity.AlertHistoryEntities;
import pk.nust.cuas.alert.infrastructure.persistence.repository.AlertJpaRepositories;

/** JPA adapter for {@link AlertRepository}. */
@Repository
public class JpaAlertRepository implements AlertRepository {

    private final AlertJpaRepositories.AlertJpa alerts;
    private final AlertJpaRepositories.AcknowledgementJpa acknowledgements;
    private final AlertJpaRepositories.EscalationJpa escalations;
    private final AlertJpaRepositories.ResolutionJpa resolutions;
    private final AlertInsertOperation insertOperation;

    public JpaAlertRepository(AlertJpaRepositories.AlertJpa alerts,
                              AlertJpaRepositories.AcknowledgementJpa acknowledgements,
                              AlertJpaRepositories.EscalationJpa escalations,
                              AlertJpaRepositories.ResolutionJpa resolutions,
                              AlertInsertOperation insertOperation) {
        this.alerts = alerts;
        this.acknowledgements = acknowledgements;
        this.escalations = escalations;
        this.resolutions = resolutions;
        this.insertOperation = insertOperation;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Deliberately not {@code @Transactional}: the insert runs in its own {@code REQUIRES_NEW}
     * transaction ({@link AlertInsertOperation}) so a lost {@code uq_alert_open_dedup} race rolls that
     * transaction back and is caught here, rather than poisoning this method's transaction — a
     * violation caught inside its own transaction still fails to commit. Losing the race is expected
     * after a consumer-group rebalance, not a fault (section 5.7), so it is reported, not thrown.
     */
    @Override
    public boolean raise(Alert alert) {
        try {
            insertOperation.insert(toEntity(alert));
            return true;
        } catch (DataIntegrityViolationException ex) {
            return false;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Alert> findOpenByDedupKey(DedupKey key) {
        return alerts.findOpenByDedupKey(key.value()).map(JpaAlertRepository::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Alert> findById(UUID alertId) {
        return alerts.findById(alertId).map(JpaAlertRepository::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Alert> findOpenByTrackId(UUID trackId) {
        return alerts.findOpenByTrackId(trackId).stream().map(JpaAlertRepository::toDomain).toList();
    }

    @Override
    @Transactional
    public void save(Alert alert) {
        AlertEntity entity = alerts.findById(alert.alertId()).orElseThrow(
                () -> new IllegalStateException("Alert " + alert.alertId() + " vanished mid-update"));
        entity.apply(
                alert.status().name(),
                alert.acknowledgedAt().orElse(null),
                alert.resolvedAt().orElse(null),
                alert.escalationLevel(),
                alert.escalatesAt().orElse(null),
                alert.occurrenceCount(),
                alert.incidentId().orElse(null),
                alert.assignedTo().orElse(null),
                Instant.now());
        alerts.save(entity);
    }

    @Override
    @Transactional
    public void recordAcknowledgement(Acknowledgement acknowledgement) {
        acknowledgements.save(new AlertHistoryEntities.AcknowledgementEntity(
                acknowledgement.alertId(), acknowledgement.actorId(), acknowledgement.actorName(),
                acknowledgement.note(), acknowledgement.occurredAt()));
    }

    @Override
    @Transactional
    public void recordEscalation(Escalation escalation) {
        escalations.save(new AlertHistoryEntities.EscalationEntity(
                escalation.alertId(), escalation.previousLevel(), escalation.currentLevel(),
                escalation.trigger().name(), escalation.actorId(), escalation.occurredAt(),
                escalation.nextEscalatesAt()));
    }

    @Override
    @Transactional
    public void recordResolution(Resolution resolution) {
        resolutions.save(new AlertHistoryEntities.ResolutionEntity(
                resolution.alertId(), resolution.disposition().name(), resolution.note(),
                resolution.actorId(), resolution.actorName(), resolution.autoResolved(),
                resolution.occurredAt()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Acknowledgement> acknowledgements(UUID alertId) {
        return acknowledgements.findByAlertIdOrderByOccurredAtAsc(alertId).stream()
                .map(e -> new Acknowledgement(e.getAlertId(), e.getActorId(), e.getActorName(),
                        e.getNote(), e.getOccurredAt()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Escalation> escalations(UUID alertId) {
        return escalations.findByAlertIdOrderByOccurredAtAsc(alertId).stream()
                .map(e -> new Escalation(e.getAlertId(), e.getPreviousLevel(), e.getCurrentLevel(),
                        EscalationTrigger.valueOf(e.getTrigger()), e.getActorId(),
                        e.getOccurredAt(), e.getNextEscalatesAt()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Alert> search(Optional<AlertStatus> status, Optional<Severity> severity,
                              Optional<String> sectorId, int limit) {
        return alerts.search(
                        status.map(Enum::name).orElse(null),
                        severity.map(Enum::name).orElse(null),
                        sectorId.orElse(null),
                        PageRequest.of(0, limit))
                .stream().map(JpaAlertRepository::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Alert> findEscalationsDue(Instant deadline, int limit) {
        return alerts.findEscalationsDue(deadline, PageRequest.of(0, limit)).stream()
                .map(JpaAlertRepository::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Alert> findAllById(List<UUID> alertIds) {
        return alerts.findAllById(alertIds).stream().map(JpaAlertRepository::toDomain).toList();
    }

    private static AlertEntity toEntity(Alert alert) {
        return new AlertEntity(
                alert.alertId(), alert.reference(), alert.dedupKey().value(), alert.status().name(),
                alert.trackId(), alert.sectorId(), alert.assessmentId().orElse(null),
                alert.severity().name(), alert.recommendation().name(), alert.threatScore(),
                alert.title(), alert.summary(), alert.raisedAt(),
                alert.acknowledgedAt().orElse(null), alert.resolvedAt().orElse(null),
                alert.escalationLevel(), alert.escalatesAt().orElse(null), alert.occurrenceCount(),
                alert.incidentId().orElse(null), alert.assignedTo().orElse(null),
                alert.correlationId(), alert.raisedAt());
    }

    private static Alert toDomain(AlertEntity entity) {
        Severity severity = Severity.valueOf(entity.getSeverity());
        return Alert.reconstitute(
                entity.getAlertId(), entity.getReference(),
                parseDedupKey(entity.getDedupKey(), entity.getTrackId(), severity),
                entity.getTrackId(), entity.getSectorId(), entity.getAssessmentId(), severity,
                Recommendation.valueOf(entity.getRecommendation()), entity.getThreatScore(),
                entity.getTitle(), entity.getSummary(), entity.getRaisedAt(),
                entity.getCorrelationId(), AlertStatus.valueOf(entity.getStatus()),
                entity.getAcknowledgedAt(), entity.getResolvedAt(), entity.getEscalationLevel(),
                entity.getEscalatesAt(), entity.getOccurrenceCount(), entity.getIncidentId(),
                entity.getAssignedTo());
    }

    /**
     * Rebuilds the dedup key from its stored form. The policy version is the only part not already
     * on the row as a typed column, so it is taken from the string; the other two come from the
     * columns, which keeps a malformed stored key from producing an alert whose key disagrees with
     * its own track and severity.
     */
    private static DedupKey parseDedupKey(String stored, UUID trackId, Severity severity) {
        String[] parts = stored.split("\\|", -1);
        String policyVersion = parts.length == 3 ? parts[2] : "";
        return new DedupKey(trackId, severity, policyVersion);
    }

    /** Kept for symmetry with the other history readers; resolutions are read via the alert row. */
    @Transactional(readOnly = true)
    public Optional<AlertDisposition> dispositionOf(UUID alertId) {
        return resolutions.findByAlertId(alertId).stream()
                .findFirst()
                .map(e -> AlertDisposition.valueOf(e.getDisposition()));
    }
}
