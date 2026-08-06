package pk.nust.cuas.alert.infrastructure.persistence;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import pk.nust.cuas.alert.domain.model.AlertDisposition;
import pk.nust.cuas.alert.domain.model.DecisionChain;
import pk.nust.cuas.alert.domain.model.EngagementAction;
import pk.nust.cuas.alert.domain.model.EvidenceKind;
import pk.nust.cuas.alert.domain.model.EvidenceRef;
import pk.nust.cuas.alert.domain.model.Incident;
import pk.nust.cuas.alert.domain.model.IncidentEvent;
import pk.nust.cuas.alert.domain.model.IncidentNote;
import pk.nust.cuas.alert.domain.model.IncidentStatus;
import pk.nust.cuas.alert.domain.model.Recommendation;
import pk.nust.cuas.alert.domain.model.Severity;
import pk.nust.cuas.alert.domain.port.IncidentRepository;
import pk.nust.cuas.alert.infrastructure.persistence.entity.IncidentDetailEntities;
import pk.nust.cuas.alert.infrastructure.persistence.entity.IncidentEntity;
import pk.nust.cuas.alert.infrastructure.persistence.repository.IncidentJpaRepositories;

/** JPA adapter for {@link IncidentRepository}. */
@Repository
public class JpaIncidentRepository implements IncidentRepository {

    private final IncidentJpaRepositories.IncidentJpa incidents;
    private final IncidentJpaRepositories.IncidentEventJpa events;
    private final IncidentJpaRepositories.IncidentNoteJpa notes;
    private final IncidentJpaRepositories.IncidentEvidenceJpa evidence;

    public JpaIncidentRepository(IncidentJpaRepositories.IncidentJpa incidents,
                                 IncidentJpaRepositories.IncidentEventJpa events,
                                 IncidentJpaRepositories.IncidentNoteJpa notes,
                                 IncidentJpaRepositories.IncidentEvidenceJpa evidence) {
        this.incidents = incidents;
        this.events = events;
        this.notes = notes;
        this.evidence = evidence;
    }

    @Override
    @Transactional
    public void create(Incident incident) {
        IncidentEntity entity = new IncidentEntity(
                incident.incidentId(), incident.reference(), incident.status().name(),
                incident.severity().name(), incident.title(), incident.sectorId(),
                incident.declaredBy(), incident.declaredByName(), incident.openedAt(),
                incident.correlationId(), incident.openedAt());
        applyState(entity, incident);
        incidents.saveAndFlush(entity);
    }

    @Override
    @Transactional
    public void save(Incident incident) {
        IncidentEntity entity = incidents.findById(incident.incidentId()).orElseThrow(
                () -> new IllegalStateException(
                        "Incident " + incident.incidentId() + " vanished mid-update"));
        applyState(entity, incident);
        incidents.save(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Incident> findById(UUID incidentId) {
        return incidents.findById(incidentId).map(JpaIncidentRepository::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Incident> search(Optional<IncidentStatus> status, int limit) {
        return incidents.search(status.map(Enum::name).orElse(null), PageRequest.of(0, limit))
                .stream().map(JpaIncidentRepository::toDomain).toList();
    }

    @Override
    @Transactional
    public void appendEvent(IncidentEvent event) {
        events.save(new IncidentDetailEntities.EventEntity(
                event.eventId(), event.incidentId(), event.sourceTopic(), event.sourceId(),
                event.summary(), event.actorId(), event.actorName(), event.occurredAt()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<IncidentEvent> timeline(UUID incidentId) {
        return events.findByIncidentIdOrderByOccurredAtAsc(incidentId).stream()
                .map(e -> new IncidentEvent(e.getEventId(), e.getIncidentId(), e.getSourceTopic(),
                        e.getSourceId(), e.getSummary(), e.getActorId(), e.getActorName(),
                        e.getOccurredAt()))
                .toList();
    }

    @Override
    @Transactional
    public void appendNote(IncidentNote note) {
        notes.save(new IncidentDetailEntities.NoteEntity(
                note.noteId(), note.incidentId(), note.authorId(), note.authorName(), note.body(),
                note.createdAt()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<IncidentNote> notes(UUID incidentId) {
        return notes.findByIncidentIdOrderByCreatedAtAsc(incidentId).stream()
                .map(e -> new IncidentNote(e.getNoteId(), e.getIncidentId(), e.getAuthorId(),
                        e.getAuthorName(), e.getBody(), e.getCreatedAt()))
                .toList();
    }

    @Override
    @Transactional
    public void attachEvidence(EvidenceRef reference) {
        evidence.save(new IncidentDetailEntities.EvidenceEntity(
                reference.evidenceId(), reference.incidentId(), reference.kind().name(),
                reference.uri(), reference.sha256(), reference.sizeBytes(),
                reference.contentType(), reference.sourceId(), reference.capturedAt()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<EvidenceRef> evidence(UUID incidentId) {
        return evidence.findByIncidentIdOrderByCapturedAtAsc(incidentId).stream()
                .map(e -> new EvidenceRef(e.getEvidenceId(), e.getIncidentId(),
                        EvidenceKind.valueOf(e.getKind()), e.getUri(), e.getSha256(),
                        e.getSizeBytes(), e.getContentType(), e.getSourceId(), e.getCapturedAt()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long countDeclaredIn(int year) {
        Instant from = LocalDate.of(year, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant to = LocalDate.of(year + 1, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant();
        return incidents.countOpenedBetween(from, to);
    }

    private static void applyState(IncidentEntity entity, Incident incident) {
        entity.apply(
                incident.status().name(), incident.severity().name(),
                incident.assignedTo().orElse(null), incident.assignedToName(),
                incident.closedAt().orElse(null),
                incident.closureDisposition().map(Enum::name).orElse(null),
                incident.closureSummary(), incident.trackIds(), incident.alertIds(), Instant.now());
        incident.decisionChain().ifPresent(chain -> entity.applyDecision(
                chain.recommendation().map(Enum::name).orElse(null),
                chain.operatorDecision(), chain.authorisedBy(), chain.authorisedByName(),
                chain.authorisedAt(), chain.auditRef(), chain.engagementAction().name()));
    }

    private static Incident toDomain(IncidentEntity entity) {
        DecisionChain chain = entity.getDecisionAuthorisedBy() == null ? null : new DecisionChain(
                entity.getDecisionSystemRecommended() == null
                        ? null : Recommendation.valueOf(entity.getDecisionSystemRecommended()),
                entity.getDecisionOperator(), entity.getDecisionAuthorisedBy(),
                entity.getDecisionAuthorisedByName(), entity.getDecisionAuthorisedAt(),
                entity.getDecisionAuditRef(),
                EngagementAction.valueOf(entity.getDecisionEngagementAction()));

        return Incident.reconstitute(
                entity.getIncidentId(), entity.getReference(),
                IncidentStatus.valueOf(entity.getStatus()), Severity.valueOf(entity.getSeverity()),
                entity.getTitle(), entity.getSectorId(), entity.getDeclaredBy(),
                entity.getDeclaredByName(), entity.getOpenedAt(), entity.getCorrelationId(),
                entity.getTrackIds(), entity.getAlertIds(), entity.getAssignedTo(),
                entity.getAssignedToName(), entity.getClosedAt(),
                entity.getClosureDisposition() == null
                        ? null : AlertDisposition.valueOf(entity.getClosureDisposition()),
                entity.getClosureSummary(), chain);
    }
}
