package pk.nust.cuas.alert.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import pk.nust.cuas.alert.domain.model.EvidenceRef;
import pk.nust.cuas.alert.domain.model.Incident;
import pk.nust.cuas.alert.domain.model.IncidentEvent;
import pk.nust.cuas.alert.domain.model.IncidentNote;

/** The incident request and response bodies (ARCHITECTURE.md sections 4.1.8, 2.4.3). */
public final class IncidentDtos {

    private IncidentDtos() {
    }

    /**
     * {@code POST /incidents}.
     *
     * <p>The declaring actor is taken from the JWT, not from the body. Section 2.4.3 records
     * incident declaration as a human act, and a body-supplied declarer would let one operator open
     * an investigation in another's name.
     *
     * @param alertIds the alerts being investigated; at least one
     * @param title    what the operator sees
     */
    @Schema(description = "Declare an incident from one or more alerts")
    public record DeclareIncidentRequest(
            @NotEmpty List<UUID> alertIds,
            @NotBlank @Size(max = 256) String title) {
    }

    /**
     * {@code POST /incidents/{id}/assign}.
     *
     * @param assigneeId   the user the investigation is assigned to
     * @param assigneeName their display name, for the timeline
     */
    @Schema(description = "Assign an investigation")
    public record AssignIncidentRequest(
            @NotBlank @Size(max = 128) String assigneeId,
            @Size(max = 256) String assigneeName) {
    }

    /**
     * {@code POST /incidents/{id}/decision} — section 2.4.3's decision chain.
     *
     * <p>There is no engagement-action field, and there is nowhere one could be supplied. The
     * platform emits advisory recommendations only; the chain records {@code NONE} structurally.
     *
     * @param operatorDecision what the operator did, which may differ from what was advised
     * @param auditRef         the audit chain reference
     */
    @Schema(description = "Record what the operator decided, beside what the system advised")
    public record RecordDecisionRequest(
            @NotBlank @Size(max = 500) String operatorDecision,
            @Size(max = 128) String auditRef) {
    }

    /**
     * {@code POST /incidents/{id}/close}.
     *
     * @param disposition the verdict
     * @param summary     the closing narrative
     */
    @Schema(description = "Close an investigation")
    public record CloseIncidentRequest(
            @NotBlank String disposition,
            @Size(max = 4000) String summary) {
    }

    /**
     * {@code POST /incidents/{id}/notes}.
     *
     * @param body the note
     */
    @Schema(description = "Append an operator note")
    public record AddNoteRequest(@NotBlank @Size(max = 4000) String body) {
    }

    /**
     * {@code POST /incidents/{id}/evidence}.
     *
     * @param kind        what it is
     * @param uri         the evidence store address — a reference, never a payload
     * @param sha256      its content hash, verified before the reference is recorded
     * @param contentType its media type
     * @param sourceId    the sensor, model or service that produced it
     */
    @Schema(description = "Attach a reference to an evidence object")
    public record AttachEvidenceRequest(
            @NotBlank String kind,
            @NotBlank @Size(max = 1024) String uri,
            @NotBlank @Size(min = 64, max = 64) String sha256,
            @Size(max = 128) String contentType,
            @Size(max = 128) String sourceId) {
    }

    /**
     * An incident as the investigation screen reads it.
     *
     * @param incidentId         its id
     * @param reference          the operator-facing handle, e.g. INC-2026-0087
     * @param status             OPEN | UNDER_REVIEW | CLOSED
     * @param severity           the severity of the worst thing in it
     * @param title              what the operator sees
     * @param sectorId           the sector
     * @param trackIds           every track under investigation
     * @param alertIds           every alert folded in
     * @param declaredBy         the declaring user — never a service
     * @param declaredByName     their display name
     * @param assignedTo         the assignee, or null
     * @param assignedToName     their display name
     * @param openedAt           when it was declared
     * @param closedAt           when it was closed, or null
     * @param closureDisposition the verdict, or null while open
     * @param closureSummary     the closing narrative
     * @param decisionChain      what was advised, decided and authorised, or null
     */
    @Schema(description = "An operator-declared investigation")
    public record IncidentResponse(
            String incidentId,
            String reference,
            String status,
            String severity,
            String title,
            String sectorId,
            List<String> trackIds,
            List<String> alertIds,
            String declaredBy,
            String declaredByName,
            String assignedTo,
            String assignedToName,
            Instant openedAt,
            Instant closedAt,
            String closureDisposition,
            String closureSummary,
            DecisionChainResponse decisionChain) {

        /**
         * @param incident the domain incident
         * @return its wire form
         */
        public static IncidentResponse from(Incident incident) {
            return new IncidentResponse(
                    incident.incidentId().toString(),
                    incident.reference(),
                    incident.status().name(),
                    incident.severity().name(),
                    incident.title(),
                    incident.sectorId(),
                    incident.trackIds().stream().map(UUID::toString).toList(),
                    incident.alertIds().stream().map(UUID::toString).toList(),
                    incident.declaredBy(),
                    incident.declaredByName(),
                    incident.assignedTo().orElse(null),
                    incident.assignedToName(),
                    incident.openedAt(),
                    incident.closedAt().orElse(null),
                    incident.closureDisposition().map(Enum::name).orElse(null),
                    incident.closureSummary(),
                    incident.decisionChain().map(DecisionChainResponse::from).orElse(null));
        }
    }

    /**
     * Section 2.4.3's decision chain.
     *
     * @param systemRecommended what threat-assessment advised, or null
     * @param operatorDecision  what the operator did
     * @param authorisedBy      who authorised it
     * @param authorisedByName  their display name
     * @param authorisedAt      when
     * @param auditRef          the audit chain reference
     * @param engagementAction  always {@code NONE} — outside system scope
     */
    @Schema(description = "What the system advised, what the operator decided, and that no "
            + "engagement action was taken")
    public record DecisionChainResponse(
            String systemRecommended,
            String operatorDecision,
            String authorisedBy,
            String authorisedByName,
            Instant authorisedAt,
            String auditRef,
            String engagementAction) {

        static DecisionChainResponse from(pk.nust.cuas.alert.domain.model.DecisionChain chain) {
            return new DecisionChainResponse(
                    chain.recommendation().map(Enum::name).orElse(null),
                    chain.operatorDecision(),
                    chain.authorisedBy(),
                    chain.authorisedByName(),
                    chain.authorisedAt(),
                    chain.auditRef(),
                    chain.engagementAction().name());
        }
    }

    /**
     * One line of the section 2.4.3 timeline.
     *
     * @param eventId     its id
     * @param sourceTopic the topic it was derived from
     * @param sourceId    the originating record
     * @param summary     the rendered line
     * @param actorId     the operator, or null for a system entry
     * @param actorName   their display name
     * @param occurredAt  when
     */
    @Schema(description = "One line of an incident timeline")
    public record TimelineEntryResponse(
            String eventId,
            String sourceTopic,
            String sourceId,
            String summary,
            String actorId,
            String actorName,
            Instant occurredAt) {

        /**
         * @param event the domain entry
         * @return its wire form
         */
        public static TimelineEntryResponse from(IncidentEvent event) {
            return new TimelineEntryResponse(
                    event.eventId().toString(), event.sourceTopic(), event.sourceId(),
                    event.summary(), event.actorId(), event.actorName(), event.occurredAt());
        }
    }

    /**
     * An operator note.
     *
     * @param noteId     its id
     * @param authorId   the author
     * @param authorName their display name
     * @param body       the note
     * @param createdAt  when
     */
    @Schema(description = "An operator note on an investigation")
    public record NoteResponse(
            String noteId, String authorId, String authorName, String body, Instant createdAt) {

        /**
         * @param note the domain note
         * @return its wire form
         */
        public static NoteResponse from(IncidentNote note) {
            return new NoteResponse(note.noteId().toString(), note.authorId(), note.authorName(),
                    note.body(), note.createdAt());
        }
    }

    /**
     * A reference to one evidence object. Never the object itself.
     *
     * @param evidenceId  its id
     * @param kind        what it is
     * @param uri         the evidence store address
     * @param sha256      its content hash — what makes an exported bundle verifiable
     * @param sizeBytes   its size
     * @param contentType its media type
     * @param sourceId    what produced it
     * @param capturedAt  when
     */
    @Schema(description = "A reference to one item in an evidence bundle")
    public record EvidenceResponse(
            String evidenceId, String kind, String uri, String sha256, long sizeBytes,
            String contentType, String sourceId, Instant capturedAt) {

        /**
         * @param evidence the domain reference
         * @return its wire form
         */
        public static EvidenceResponse from(EvidenceRef evidence) {
            return new EvidenceResponse(evidence.evidenceId().toString(), evidence.kind().name(),
                    evidence.uri(), evidence.sha256(), evidence.sizeBytes(),
                    evidence.contentType(), evidence.sourceId(), evidence.capturedAt());
        }
    }
}
