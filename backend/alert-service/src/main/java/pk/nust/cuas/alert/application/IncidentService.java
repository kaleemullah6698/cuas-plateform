package pk.nust.cuas.alert.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pk.nust.cuas.alert.domain.exception.AlertNotFoundException;
import pk.nust.cuas.alert.domain.exception.IllegalAlertTransitionException;
import pk.nust.cuas.alert.domain.exception.IncidentNotFoundException;
import pk.nust.cuas.alert.domain.model.Alert;
import pk.nust.cuas.alert.domain.model.AlertDisposition;
import pk.nust.cuas.alert.domain.model.DecisionChain;
import pk.nust.cuas.alert.domain.model.EvidenceRef;
import pk.nust.cuas.alert.domain.model.Incident;
import pk.nust.cuas.alert.domain.model.IncidentEvent;
import pk.nust.cuas.alert.domain.model.IncidentNote;
import pk.nust.cuas.alert.domain.model.Severity;
import pk.nust.cuas.alert.domain.port.AlertEventPublisher;
import pk.nust.cuas.alert.domain.port.AlertRepository;
import pk.nust.cuas.alert.domain.port.EvidenceStore;
import pk.nust.cuas.alert.domain.port.IncidentRepository;
import pk.nust.cuas.commons.audit.ActorType;
import pk.nust.cuas.commons.audit.AuditEvent;
import pk.nust.cuas.commons.audit.AuditOutcome;
import pk.nust.cuas.commons.audit.AuditPublisher;
import pk.nust.cuas.commons.correlation.CorrelationId;
import pk.nust.cuas.commons.id.Uuidv7Generator;

/**
 * Incident declaration, investigation and closure (ARCHITECTURE.md sections 4.1.8, 2.4.3).
 *
 * <p><strong>An incident is always declared by a person, and the audit record proves it.</strong>
 * Declaring and closing are both audited fail-closed (section 7.5): the record is written inside the
 * transaction and before the change commits, so an incident that could not be recorded is not
 * declared. Section 2.4.3's decision chain asserts that a human made the call, and an unaudited
 * declaration would leave that assertion resting on nothing.
 */
@Service
public class IncidentService {

    private static final Logger log = LoggerFactory.getLogger(IncidentService.class);

    /** Audit actions. Domain-specific names, per {@code AuditActions}. */
    public static final String INCIDENT_DECLARED = "INCIDENT_DECLARED";
    public static final String INCIDENT_CLOSED = "INCIDENT_CLOSED";
    private static final String RESOURCE_TYPE = "INCIDENT";

    private final IncidentRepository incidents;
    private final AlertRepository alerts;
    private final AlertEventPublisher events;
    private final EvidenceStore evidenceStore;
    private final AlertReferenceGenerator references;
    private final AuditPublisher audit;
    private final Uuidv7Generator ids;
    private final Clock clock;

    @SuppressWarnings("checkstyle:ParameterNumber")
    public IncidentService(IncidentRepository incidents, AlertRepository alerts,
                           AlertEventPublisher events, EvidenceStore evidenceStore,
                           AlertReferenceGenerator references, AuditPublisher audit,
                           Uuidv7Generator ids, Clock clock) {
        this.incidents = incidents;
        this.alerts = alerts;
        this.events = events;
        this.evidenceStore = evidenceStore;
        this.references = references;
        this.audit = audit;
        this.ids = ids;
        this.clock = clock;
    }

    /**
     * Declares an incident from one or more alerts.
     *
     * @param alertIds       the alerts being investigated; at least one
     * @param title          what the operator sees
     * @param declaredBy     the declaring user id — never a service
     * @param declaredByName their display name
     * @return the incident
     * @throws pk.nust.cuas.commons.audit.AuditWriteException when the declaration cannot be
     *         recorded, in which case nothing is declared
     */
    @Transactional
    public Incident declare(List<UUID> alertIds, String title, String declaredBy,
                            String declaredByName) {
        if (alertIds == null || alertIds.isEmpty()) {
            throw new IllegalArgumentException(
                    "An incident is declared from at least one alert; declaring one from nothing"
                            + " would leave an investigation with no evidence to start from");
        }
        List<Alert> sources = alerts.findAllById(alertIds);
        if (sources.size() != alertIds.size()) {
            throw new AlertNotFoundException(alertIds.stream()
                    .filter(id -> sources.stream().noneMatch(a -> a.alertId().equals(id)))
                    .findFirst().orElseThrow());
        }

        Instant now = Instant.now(clock);
        Alert first = sources.get(0);
        Incident incident = Incident.declare(
                ids.generate(now),
                references.nextIncidentReference(now),
                Severity.LOW,
                title,
                first.sectorId(),
                declaredBy,
                declaredByName,
                now,
                first.correlationId());
        sources.forEach(incident::include);

        audit.publish(AuditEvent.of(INCIDENT_DECLARED, RESOURCE_TYPE)
                .auditId(UUID.randomUUID())
                .occurredAt(now)
                .actor(declaredBy, ActorType.USER)
                .resourceId(incident.incidentId().toString())
                .outcome(AuditOutcome.SUCCESS)
                .correlationId(CorrelationId.currentOrNew())
                .details(Map.of(
                        "reference", incident.reference(),
                        "severity", incident.severity().name(),
                        "alertIds", alertIds.toString()))
                .build());

        incidents.create(incident);
        sources.forEach(alert -> {
            alert.attachToIncident(incident.incidentId());
            alerts.save(alert);
            appendSystemEvent(incident, "alert.raised.v1", alert.alertId().toString(),
                    alert.reference() + " raised at " + alert.severity(), alert.raisedAt());
        });
        appendOperatorEvent(incident, "incident.created.v1", incident.incidentId().toString(),
                declaredByName + " declared " + incident.reference(), declaredBy, declaredByName,
                now);

        events.publishIncidentCreated(incident);
        log.info("Incident {} declared by {} from {} alert(s)",
                incident.reference(), declaredBy, sources.size());
        return incident;
    }

    /**
     * Assigns an investigation, moving it to {@code UNDER_REVIEW}.
     *
     * @param incidentId the investigation
     * @param assigneeId the assignee
     * @param assigneeName their display name
     * @param actorId    who assigned it
     * @return the incident
     */
    @Transactional
    public Incident assign(UUID incidentId, String assigneeId, String assigneeName,
                           String actorId) {
        Incident incident = require(incidentId);
        Instant now = Instant.now(clock);
        mutate(incident, () -> incident.assignTo(assigneeId, assigneeName));
        incidents.save(incident);
        appendOperatorEvent(incident, "incident.updated.v1", incidentId.toString(),
                "assigned to " + assigneeName, actorId, "", now);
        events.publishIncidentUpdated(incident);
        return incident;
    }

    /**
     * Records the decision chain — what was advised, what was decided, and by whom (section 2.4.3).
     *
     * @param incidentId       the investigation
     * @param operatorDecision what the operator did
     * @param authorisedBy     the authorising user id
     * @param authorisedByName their display name
     * @param auditRef         the audit chain reference
     * @return the incident
     */
    @Transactional
    public Incident recordDecision(UUID incidentId, String operatorDecision, String authorisedBy,
                                   String authorisedByName, String auditRef) {
        Incident incident = require(incidentId);
        Instant now = Instant.now(clock);
        var advised = alerts.findAllById(List.copyOf(incident.alertIds())).stream()
                .findFirst()
                .map(Alert::recommendation)
                .orElse(null);

        mutate(incident, () -> incident.recordDecision(DecisionChain.of(
                advised, operatorDecision, authorisedBy, authorisedByName, now, auditRef)));
        incidents.save(incident);
        appendOperatorEvent(incident, "incident.updated.v1", incidentId.toString(),
                "decision recorded: " + operatorDecision, authorisedBy, authorisedByName, now);
        events.publishIncidentUpdated(incident);
        return incident;
    }

    /**
     * Closes an investigation.
     *
     * @param incidentId  the investigation
     * @param disposition the verdict
     * @param summary     the closing narrative
     * @param actorId     the closing user
     * @param actorName   their display name
     * @return the incident
     */
    @Transactional
    public Incident close(UUID incidentId, AlertDisposition disposition, String summary,
                          String actorId, String actorName) {
        Incident incident = require(incidentId);
        Instant now = Instant.now(clock);

        audit.publish(AuditEvent.of(INCIDENT_CLOSED, RESOURCE_TYPE)
                .auditId(UUID.randomUUID())
                .occurredAt(now)
                .actor(actorId, ActorType.USER)
                .resourceId(incidentId.toString())
                .outcome(AuditOutcome.SUCCESS)
                .correlationId(CorrelationId.currentOrNew())
                .details(Map.of(
                        "reference", incident.reference(),
                        "disposition", disposition.name(),
                        "summary", summary == null ? "" : summary))
                .build());

        mutate(incident, () -> incident.close(disposition, summary, now));
        incidents.save(incident);
        appendOperatorEvent(incident, "incident.closed.v1", incidentId.toString(),
                actorName + " closed " + incident.reference() + " as " + disposition,
                actorId, actorName, now);
        events.publishIncidentClosed(incident);
        log.info("Incident {} closed by {} as {}", incident.reference(), actorId, disposition);
        return incident;
    }

    /**
     * Appends an operator note.
     *
     * @param incidentId the investigation
     * @param body       the note
     * @param authorId   the author
     * @param authorName their display name
     * @return the note
     */
    @Transactional
    public IncidentNote addNote(UUID incidentId, String body, String authorId, String authorName) {
        Incident incident = require(incidentId);
        Instant now = Instant.now(clock);
        IncidentNote note =
                new IncidentNote(ids.generate(now), incidentId, authorId, authorName, body, now);
        incidents.appendNote(note);
        appendOperatorEvent(incident, "incident.updated.v1", note.noteId().toString(),
                "note added", authorId, authorName, now);
        return note;
    }

    /**
     * Attaches a piece of evidence, verifying it exists and hashes as claimed before recording it.
     *
     * <p>Verification is at attach time, not at export time. A bundle assembled from references that
     * were never checked would fail at the moment it is most needed — when a reviewer asks for it —
     * and by then the object may be long gone.
     *
     * @param incidentId  the investigation
     * @param kind        what it is
     * @param uri         the evidence store address
     * @param sha256      its content hash
     * @param contentType its media type
     * @param sourceId    what produced it
     * @return the reference
     */
    @Transactional
    public EvidenceRef attachEvidence(UUID incidentId, pk.nust.cuas.alert.domain.model.EvidenceKind kind,
                                      String uri, String sha256, String contentType,
                                      String sourceId) {
        Incident incident = require(incidentId);
        if (!evidenceStore.verify(uri, sha256)) {
            throw new IllegalArgumentException(
                    "The evidence object at " + uri + " is missing or does not match the supplied"
                            + " hash. Recording an unverifiable reference would produce a bundle"
                            + " that fails when a reviewer asks for it.");
        }
        Instant now = Instant.now(clock);
        EvidenceRef evidence = new EvidenceRef(
                ids.generate(now), incidentId, kind, uri, sha256,
                evidenceStore.sizeOf(uri).orElse(0L), contentType, sourceId, now);
        incidents.attachEvidence(evidence);
        appendSystemEvent(incident, "incident.updated.v1", evidence.evidenceId().toString(),
                kind + " evidence attached", now);
        return evidence;
    }

    // ── Reads ───────────────────────────────────────────────────────────────────────────────────

    /**
     * @param incidentId the investigation
     * @return it
     */
    @Transactional(readOnly = true)
    public Incident get(UUID incidentId) {
        return require(incidentId);
    }

    /**
     * @param status filter by status when given
     * @param limit  the maximum number to return
     * @return incidents, newest opened first
     */
    @Transactional(readOnly = true)
    public List<Incident> list(Optional<pk.nust.cuas.alert.domain.model.IncidentStatus> status,
                               int limit) {
        return incidents.search(status, limit);
    }

    /**
     * @param incidentId the investigation
     * @return its timeline, oldest first
     */
    @Transactional(readOnly = true)
    public List<IncidentEvent> timeline(UUID incidentId) {
        require(incidentId);
        return incidents.timeline(incidentId);
    }

    /**
     * @param incidentId the investigation
     * @return its notes, oldest first
     */
    @Transactional(readOnly = true)
    public List<IncidentNote> notes(UUID incidentId) {
        require(incidentId);
        return incidents.notes(incidentId);
    }

    /**
     * @param incidentId the investigation
     * @return its evidence bundle
     */
    @Transactional(readOnly = true)
    public List<EvidenceRef> evidence(UUID incidentId) {
        require(incidentId);
        return incidents.evidence(incidentId);
    }

    private Incident require(UUID incidentId) {
        return incidents.findById(incidentId)
                .orElseThrow(() -> new IncidentNotFoundException(incidentId));
    }

    private void mutate(Incident incident, Runnable change) {
        try {
            change.run();
        } catch (IllegalStateException ex) {
            throw new IllegalAlertTransitionException(incident.reference(), ex.getMessage());
        }
    }

    private void appendSystemEvent(Incident incident, String topic, String sourceId,
                                   String summary, Instant at) {
        incidents.appendEvent(IncidentEvent.system(
                ids.generate(at), incident.incidentId(), topic, sourceId, summary, at));
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    private void appendOperatorEvent(Incident incident, String topic, String sourceId,
                                     String summary, String actorId, String actorName, Instant at) {
        incidents.appendEvent(IncidentEvent.byOperator(
                ids.generate(at), incident.incidentId(), topic, sourceId, summary,
                actorId, actorName, at));
    }
}
