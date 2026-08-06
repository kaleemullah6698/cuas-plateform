package pk.nust.cuas.alert.common;

import com.google.protobuf.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import pk.nust.cuas.alert.domain.model.Acknowledgement;
import pk.nust.cuas.alert.domain.model.Alert;
import pk.nust.cuas.alert.domain.model.AlertDisposition;
import pk.nust.cuas.alert.domain.model.AlertStatus;
import pk.nust.cuas.alert.domain.model.EscalationTrigger;
import pk.nust.cuas.alert.domain.model.Escalation;
import pk.nust.cuas.alert.domain.model.Incident;
import pk.nust.cuas.alert.domain.model.IncidentStatus;
import pk.nust.cuas.alert.domain.model.Recommendation;
import pk.nust.cuas.alert.domain.model.Resolution;
import pk.nust.cuas.alert.domain.model.Severity;
import pk.nust.cuas.alert.domain.service.ThreatTransition;
import pk.nust.cuas.threat.v1.ThreatLevel;
import pk.nust.cuas.threat.v1.ThreatLevelChanged;

/**
 * Translates between the domain models and the generated contract types (ARCHITECTURE.md sections
 * 5.3.3, 5.3.4).
 *
 * <p>Lives in {@code common/} because both the consumers and the publisher need it, and because the
 * domain must not see a generated type: {@code domain/} compiles without Protobuf on the classpath,
 * which is the layering test CLAUDE.md section 2 sets.
 *
 * <p>One mapper for both directions so the topics this service consumes and the topics it publishes
 * cannot come to describe severity, or a recommendation, differently.
 */
@Component
public class AlertProtoMapper {

    /** The schema version stamped on every event this service publishes (section 5.5). */
    public static final String SCHEMA_VERSION = "1.0.0";

    /**
     * Reads a threat level change.
     *
     * @param message the event
     * @return the transition, or empty when the message names no usable track or carries no assessed
     *         level. An unassessed level is not an alertable event: a track nothing could judge has
     *         not become safe, and raising an alert for the absence of a judgement would be worse
     *         than staying quiet.
     */
    public Optional<ThreatTransition> toTransition(ThreatLevelChanged message) {
        Optional<UUID> trackId = parseUuid(message.getTrackId());
        Optional<Severity> current = toSeverity(message.getCurrentLevel());
        if (trackId.isEmpty() || current.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new ThreatTransition(
                trackId.get(),
                message.getSectorId(),
                parseUuid(message.getAssessmentId()).orElse(null),
                toSeverity(message.getPreviousLevel()),
                current.get(),
                message.getCurrentScore(),
                toRecommendation(message.getRecommendation()),
                // The dedup key needs the policy version. threat.level.changed.v1 does not carry
                // one — the assessment does — so the assessment id stands in: it changes whenever
                // the policy does, which is the property the key actually needs.
                message.getAssessmentId(),
                message.hasChangedAt() ? toInstant(message.getChangedAt()) : Instant.EPOCH,
                message.getCorrelationId()));
    }

    /**
     * @param alert the alert to publish
     * @return its contract form for {@code alert.raised.v1}
     */
    public pk.nust.cuas.alert.v1.Alert toProto(Alert alert) {
        pk.nust.cuas.alert.v1.Alert.Builder builder = pk.nust.cuas.alert.v1.Alert.newBuilder()
                .setAlertId(alert.alertId().toString())
                .setReference(alert.reference())
                .setStatus(toProto(alert.status()))
                .setDedupKey(alert.dedupKey().value())
                .setSeverity(toProto(alert.severity()))
                .setRecommendation(toProto(alert.recommendation()))
                .setThreatScore((float) alert.threatScore())
                .setTrackId(alert.trackId().toString())
                .setSectorId(alert.sectorId())
                .setTitle(alert.title())
                .setSummary(alert.summary())
                .setRaisedAt(toTimestamp(alert.raisedAt()))
                .setEscalationLevel(alert.escalationLevel())
                .setOccurrenceCount(alert.occurrenceCount())
                .setSchemaVersion(SCHEMA_VERSION)
                .setCorrelationId(alert.correlationId());
        alert.assessmentId().ifPresent(id -> builder.setAssessmentId(id.toString()));
        alert.acknowledgedAt().ifPresent(at -> builder.setAcknowledgedAt(toTimestamp(at)));
        alert.resolvedAt().ifPresent(at -> builder.setResolvedAt(toTimestamp(at)));
        alert.escalatesAt().ifPresent(at -> builder.setEscalatesAt(toTimestamp(at)));
        alert.incidentId().ifPresent(id -> builder.setIncidentId(id.toString()));
        return builder.build();
    }

    /**
     * @param alert           the alert
     * @param acknowledgement the acknowledgement
     * @return its contract form for {@code alert.acknowledged.v1}
     */
    public pk.nust.cuas.alert.v1.AlertAcknowledged toProto(Alert alert,
                                                          Acknowledgement acknowledgement) {
        return pk.nust.cuas.alert.v1.AlertAcknowledged.newBuilder()
                .setAlertId(alert.alertId().toString())
                .setReference(alert.reference())
                .setTrackId(alert.trackId().toString())
                .setActorId(acknowledgement.actorId())
                .setActorDisplayName(acknowledgement.actorName())
                .setNote(acknowledgement.note())
                .setAcknowledgedAt(toTimestamp(acknowledgement.occurredAt()))
                .setSchemaVersion(SCHEMA_VERSION)
                .setCorrelationId(alert.correlationId())
                .build();
    }

    /**
     * @param alert      the alert
     * @param escalation the escalation
     * @return its contract form for {@code alert.escalated.v1}
     */
    public pk.nust.cuas.alert.v1.AlertEscalated toProto(Alert alert, Escalation escalation) {
        pk.nust.cuas.alert.v1.AlertEscalated.Builder builder =
                pk.nust.cuas.alert.v1.AlertEscalated.newBuilder()
                        .setAlertId(alert.alertId().toString())
                        .setReference(alert.reference())
                        .setTrackId(alert.trackId().toString())
                        .setPreviousLevel(escalation.previousLevel())
                        .setCurrentLevel(escalation.currentLevel())
                        .setTrigger(toProto(escalation.trigger()))
                        .setSeverity(toProto(alert.severity()))
                        .setEscalatedAt(toTimestamp(escalation.occurredAt()))
                        .setSchemaVersion(SCHEMA_VERSION)
                        .setCorrelationId(alert.correlationId());
        escalation.nextAt().ifPresent(at -> builder.setNextEscalateAt(toTimestamp(at)));
        return builder.build();
    }

    /**
     * @param alert      the alert
     * @param resolution the resolution
     * @return its contract form for {@code alert.resolved.v1}
     */
    public pk.nust.cuas.alert.v1.AlertResolved toProto(Alert alert, Resolution resolution) {
        pk.nust.cuas.alert.v1.AlertResolved.Builder builder =
                pk.nust.cuas.alert.v1.AlertResolved.newBuilder()
                        .setAlertId(alert.alertId().toString())
                        .setReference(alert.reference())
                        .setTrackId(alert.trackId().toString())
                        .setDisposition(toProto(resolution.disposition()))
                        .setNote(resolution.note())
                        .setActorDisplayName(resolution.actorName())
                        .setAutoResolved(resolution.autoResolved())
                        .setResolvedAt(toTimestamp(resolution.occurredAt()))
                        .setSchemaVersion(SCHEMA_VERSION)
                        .setCorrelationId(alert.correlationId());
        resolution.actor().ifPresent(builder::setActorId);
        return builder.build();
    }

    /**
     * @param incident the incident to publish
     * @return its contract form for the three {@code incident.*} topics
     */
    public pk.nust.cuas.alert.v1.Incident toProto(Incident incident) {
        pk.nust.cuas.alert.v1.Incident.Builder builder =
                pk.nust.cuas.alert.v1.Incident.newBuilder()
                        .setIncidentId(incident.incidentId().toString())
                        .setReference(incident.reference())
                        .setStatus(toProto(incident.status()))
                        .setSeverity(toProto(incident.severity()))
                        .setTitle(incident.title())
                        .setSectorId(incident.sectorId())
                        .setDeclaredBy(incident.declaredBy())
                        .setDeclaredByName(incident.declaredByName())
                        .setOpenedAt(toTimestamp(incident.openedAt()))
                        .setSchemaVersion(SCHEMA_VERSION)
                        .setCorrelationId(incident.correlationId());
        incident.trackIds().forEach(id -> builder.addTrackIds(id.toString()));
        incident.alertIds().forEach(id -> builder.addAlertIds(id.toString()));
        incident.assignedTo().ifPresent(builder::setAssignedTo);
        builder.setAssignedToName(incident.assignedToName());
        incident.closedAt().ifPresent(at -> builder.setClosedAt(toTimestamp(at)));
        incident.closureDisposition().ifPresent(d -> builder.setClosureDisposition(toProto(d)));
        builder.setClosureSummary(incident.closureSummary());
        incident.decisionChain().ifPresent(chain -> {
            pk.nust.cuas.alert.v1.DecisionChain.Builder decision =
                    pk.nust.cuas.alert.v1.DecisionChain.newBuilder()
                            .setOperatorDecision(chain.operatorDecision())
                            .setAuthorisedByName(chain.authorisedByName())
                            .setAuditRef(chain.auditRef())
                            // The one legal value. Set explicitly so an exported bundle asserts it
                            // rather than leaving it to be inferred from an unset field.
                            .setEngagementAction(
                                    pk.nust.cuas.alert.v1.EngagementAction.ENGAGEMENT_ACTION_NONE);
            chain.recommendation().ifPresent(r -> decision.setSystemRecommended(toProto(r)));
            if (chain.authorisedBy() != null) {
                decision.setAuthorisedBy(chain.authorisedBy());
            }
            if (chain.authorisedAt() != null) {
                decision.setAuthorisedAt(toTimestamp(chain.authorisedAt()));
            }
            builder.setDecisionChain(decision);
        });
        return builder.build();
    }

    // ── Enum translation ────────────────────────────────────────────────────────────────────────

    /**
     * @return the severity, or empty for {@code THREAT_LEVEL_UNSPECIFIED} — which means the track is
     *         <em>unassessed</em> (section 5.3.3), not that it is low. Mapping it to LOW would let a
     *         track nothing could judge raise an alert claiming it had been judged harmless.
     */
    private static Optional<Severity> toSeverity(ThreatLevel level) {
        return switch (level) {
            case THREAT_LEVEL_LOW -> Optional.of(Severity.LOW);
            case THREAT_LEVEL_MEDIUM -> Optional.of(Severity.MEDIUM);
            case THREAT_LEVEL_HIGH -> Optional.of(Severity.HIGH);
            case THREAT_LEVEL_CRITICAL -> Optional.of(Severity.CRITICAL);
            default -> Optional.empty();
        };
    }

    private static ThreatLevel toProto(Severity severity) {
        return switch (severity) {
            case LOW -> ThreatLevel.THREAT_LEVEL_LOW;
            case MEDIUM -> ThreatLevel.THREAT_LEVEL_MEDIUM;
            case HIGH -> ThreatLevel.THREAT_LEVEL_HIGH;
            case CRITICAL -> ThreatLevel.THREAT_LEVEL_CRITICAL;
        };
    }

    /**
     * @return the advisory, defaulting to {@code RECOMMEND_OPERATOR_REVIEW} for an unrecognised
     *         value. Defaulting to MONITOR would be the quieter choice and the wrong one: an advisory
     *         this build cannot interpret is a reason for a person to look, not an all-clear.
     */
    private static Recommendation toRecommendation(pk.nust.cuas.threat.v1.Recommendation value) {
        return switch (value) {
            case MONITOR -> Recommendation.MONITOR;
            case TRACK_AND_MONITOR -> Recommendation.TRACK_AND_MONITOR;
            case REQUEST_VISUAL_ID -> Recommendation.REQUEST_VISUAL_ID;
            case ESCALATE_TO_SUPERVISOR -> Recommendation.ESCALATE_TO_SUPERVISOR;
            default -> Recommendation.RECOMMEND_OPERATOR_REVIEW;
        };
    }

    private static pk.nust.cuas.threat.v1.Recommendation toProto(Recommendation recommendation) {
        return switch (recommendation) {
            case MONITOR -> pk.nust.cuas.threat.v1.Recommendation.MONITOR;
            case TRACK_AND_MONITOR -> pk.nust.cuas.threat.v1.Recommendation.TRACK_AND_MONITOR;
            case REQUEST_VISUAL_ID -> pk.nust.cuas.threat.v1.Recommendation.REQUEST_VISUAL_ID;
            case ESCALATE_TO_SUPERVISOR ->
                    pk.nust.cuas.threat.v1.Recommendation.ESCALATE_TO_SUPERVISOR;
            case RECOMMEND_OPERATOR_REVIEW ->
                    pk.nust.cuas.threat.v1.Recommendation.RECOMMEND_OPERATOR_REVIEW;
        };
    }

    private static pk.nust.cuas.alert.v1.AlertStatus toProto(AlertStatus status) {
        return switch (status) {
            case RAISED -> pk.nust.cuas.alert.v1.AlertStatus.ALERT_STATUS_RAISED;
            case ACKNOWLEDGED -> pk.nust.cuas.alert.v1.AlertStatus.ALERT_STATUS_ACKNOWLEDGED;
            case ESCALATED -> pk.nust.cuas.alert.v1.AlertStatus.ALERT_STATUS_ESCALATED;
            case RESOLVED -> pk.nust.cuas.alert.v1.AlertStatus.ALERT_STATUS_RESOLVED;
            case SUPPRESSED -> pk.nust.cuas.alert.v1.AlertStatus.ALERT_STATUS_SUPPRESSED;
        };
    }

    private static pk.nust.cuas.alert.v1.AlertDisposition toProto(AlertDisposition disposition) {
        return switch (disposition) {
            case TRUE_POSITIVE ->
                    pk.nust.cuas.alert.v1.AlertDisposition.ALERT_DISPOSITION_TRUE_POSITIVE;
            case FALSE_POSITIVE ->
                    pk.nust.cuas.alert.v1.AlertDisposition.ALERT_DISPOSITION_FALSE_POSITIVE;
            case BENIGN -> pk.nust.cuas.alert.v1.AlertDisposition.ALERT_DISPOSITION_BENIGN;
            case DUPLICATE -> pk.nust.cuas.alert.v1.AlertDisposition.ALERT_DISPOSITION_DUPLICATE;
            case INCONCLUSIVE ->
                    pk.nust.cuas.alert.v1.AlertDisposition.ALERT_DISPOSITION_INCONCLUSIVE;
        };
    }

    private static pk.nust.cuas.alert.v1.EscalationTrigger toProto(EscalationTrigger trigger) {
        return switch (trigger) {
            case UNACKNOWLEDGED ->
                    pk.nust.cuas.alert.v1.EscalationTrigger.ESCALATION_TRIGGER_UNACKNOWLEDGED;
            case LEVEL_INCREASE ->
                    pk.nust.cuas.alert.v1.EscalationTrigger.ESCALATION_TRIGGER_LEVEL_INCREASE;
            case MANUAL -> pk.nust.cuas.alert.v1.EscalationTrigger.ESCALATION_TRIGGER_MANUAL;
        };
    }

    private static pk.nust.cuas.alert.v1.IncidentStatus toProto(IncidentStatus status) {
        return switch (status) {
            case OPEN -> pk.nust.cuas.alert.v1.IncidentStatus.INCIDENT_STATUS_OPEN;
            case UNDER_REVIEW ->
                    pk.nust.cuas.alert.v1.IncidentStatus.INCIDENT_STATUS_UNDER_REVIEW;
            case CLOSED -> pk.nust.cuas.alert.v1.IncidentStatus.INCIDENT_STATUS_CLOSED;
        };
    }

    /**
     * @param id an id from a message
     * @return the parsed id, or empty when the message is not for something this service can act on.
     *         Reported rather than thrown: a malformed id is a fact about the message, and a
     *         consumer's job is to skip it rather than block the partition behind it.
     */
    public static Optional<UUID> parseUuid(String id) {
        try {
            return Optional.of(UUID.fromString(id));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private static Instant toInstant(Timestamp timestamp) {
        return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
    }

    private static Timestamp toTimestamp(Instant instant) {
        return Timestamp.newBuilder()
                .setSeconds(instant.getEpochSecond())
                .setNanos(instant.getNano())
                .build();
    }
}
