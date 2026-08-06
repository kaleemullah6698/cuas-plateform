package pk.nust.cuas.threat.common;

import com.google.protobuf.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import pk.nust.cuas.threat.domain.model.AssetApproach;
import pk.nust.cuas.threat.domain.model.InferenceObservation;
import pk.nust.cuas.threat.domain.model.ThreatAssessment;
import pk.nust.cuas.threat.domain.model.ThreatFactor;
import pk.nust.cuas.threat.domain.model.ThreatFactorType;
import pk.nust.cuas.threat.domain.model.ThreatLevel;
import pk.nust.cuas.threat.domain.model.ThreatLevelTransition;
import pk.nust.cuas.threat.domain.model.TrackSnapshot;
import pk.nust.cuas.track.v1.ClosestPointOfApproach;
import pk.nust.cuas.track.v1.Track;

/**
 * Translates between the domain models and the generated contract types (ARCHITECTURE.md sections
 * 5.3.2, 5.3.3).
 *
 * <p>Lives in {@code common/} rather than in either adapter because both the consumers and the
 * publisher need it, and because the domain must not see a generated type: {@code domain/} compiles
 * without Protobuf on the classpath, which is the layering test CLAUDE.md section 2 sets.
 *
 * <p>One mapper for both directions, shared by every surface, so the topics this service consumes
 * and the topics it publishes cannot come to describe the same concepts differently.
 */
@Component
public class ThreatProtoMapper {

    /** The schema version stamped on every event this service publishes (section 5.5). */
    public static final String SCHEMA_VERSION = "1.0.0";

    /**
     * Extracts the scoring-relevant facts from a published track.
     *
     * <p>{@code observed_at} is used, not {@code last_seen}. Section 5.3.2 records why the two
     * diverge: {@code observed_at} is the time the estimate is valid for, which a coasting
     * prediction advances past the last detection. Scoring a prediction against the clock of the
     * detection it was extrapolated from would date every coasting assessment to before the state it
     * describes.
     *
     * @param track the published track
     * @return the snapshot, or empty when the message carries no usable track id
     */
    public Optional<TrackSnapshot> toSnapshot(Track track) {
        Optional<UUID> trackId = parseUuid(track.getTrackId());
        if (trackId.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new TrackSnapshot(
                trackId.get(),
                track.getSectorId(),
                track.hasObservedAt() ? toInstant(track.getObservedAt()) : Instant.EPOCH,
                track.getSpeedMs(),
                track.hasNearestAssetCpa()
                        ? Optional.of(toApproach(track.getNearestAssetCpa()))
                        : Optional.empty(),
                track.getCorrelationId()));
    }

    /**
     * @param assessment the assessment to publish
     * @return its contract form for {@code threat.assessed.v1}
     */
    public pk.nust.cuas.threat.v1.ThreatAssessment toProto(ThreatAssessment assessment) {
        pk.nust.cuas.threat.v1.ThreatAssessment.Builder builder =
                pk.nust.cuas.threat.v1.ThreatAssessment.newBuilder()
                        .setAssessmentId(assessment.assessmentId().toString())
                        .setTrackId(assessment.trackId().toString())
                        .setSectorId(assessment.sectorId())
                        .setCompositeScore((float) assessment.compositeScore())
                        .setLevel(toProto(assessment.level()))
                        .setConfidence(pk.nust.cuas.threat.v1.AssessmentConfidence.newBuilder()
                                .setAvailableWeight((float) assessment.confidence().availableWeight())
                                .setFactorsAvailable(assessment.confidence().factorsAvailable())
                                .setFactorsTotal(assessment.confidence().factorsTotal())
                                .setIsReliable(assessment.confidence().isReliable()))
                        .setRecommendation(toProto(
                                assessment.recommendation().recommendation()))
                        .setPolicyId(assessment.policyId())
                        .setPolicyVersion(assessment.policyVersion())
                        .setPolicyEffectiveAt(toTimestamp(assessment.policyEffectiveAt()))
                        .setAssessedAt(toTimestamp(assessment.assessedAt()))
                        .setObservedAt(toTimestamp(assessment.observedAt()))
                        .setSchemaVersion(SCHEMA_VERSION)
                        .setCorrelationId(assessment.correlationId());
        assessment.factors().forEach(factor -> builder.addFactors(toProto(factor)));
        return builder.build();
    }

    /**
     * @param transition the transition to publish
     * @return its contract form for {@code threat.level.changed.v1}
     */
    public pk.nust.cuas.threat.v1.ThreatLevelChanged toProto(ThreatLevelTransition transition) {
        return pk.nust.cuas.threat.v1.ThreatLevelChanged.newBuilder()
                .setTrackId(transition.trackId().toString())
                .setSectorId(transition.sectorId())
                .setAssessmentId(transition.assessmentId().toString())
                .setPreviousLevel(toProto(transition.previousLevel()))
                .setCurrentLevel(toProto(Optional.of(transition.currentLevel())))
                .setPreviousScore((float) transition.previousScore())
                .setCurrentScore((float) transition.currentScore())
                .setEscalation(transition.isEscalation())
                .setRecommendation(toProto(transition.recommendation().recommendation()))
                .setChangedAt(toTimestamp(transition.changedAt()))
                .setSchemaVersion(SCHEMA_VERSION)
                .setCorrelationId(transition.correlationId())
                .build();
    }

    /**
     * @param assessment the assessment whose advice to publish
     * @param recommendationId a fresh id for the advisory
     * @return its contract form for {@code threat.recommendation.generated.v1}
     */
    public pk.nust.cuas.threat.v1.ThreatRecommendationGenerated toRecommendationProto(
            ThreatAssessment assessment, UUID recommendationId) {
        return pk.nust.cuas.threat.v1.ThreatRecommendationGenerated.newBuilder()
                .setRecommendationId(recommendationId.toString())
                .setTrackId(assessment.trackId().toString())
                .setAssessmentId(assessment.assessmentId().toString())
                .setRecommendation(toProto(assessment.recommendation().recommendation()))
                .setLevel(toProto(assessment.level()))
                .setRationale(assessment.recommendation().rationale())
                .setRuleId(assessment.recommendation().ruleId())
                .setPolicyVersion(assessment.policyVersion())
                .setGeneratedAt(toTimestamp(assessment.assessedAt()))
                .setSchemaVersion(SCHEMA_VERSION)
                .setCorrelationId(assessment.correlationId())
                .build();
    }

    private static pk.nust.cuas.threat.v1.ThreatFactor toProto(ThreatFactor factor) {
        return pk.nust.cuas.threat.v1.ThreatFactor.newBuilder()
                .setType(toProto(factor.type()))
                .setAvailable(factor.isAvailable())
                .setValue(factor.value().orElse(0.0).floatValue())
                .setWeight((float) factor.weight())
                .setContribution((float) factor.contribution())
                .setUnavailableReason(factor.unavailableReason().orElse(""))
                .build();
    }

    /**
     * An absent level maps to {@code THREAT_LEVEL_UNSPECIFIED}, which is the contract's way of
     * saying unassessed (section 5.3.3) — not to LOW.
     */
    private static pk.nust.cuas.threat.v1.ThreatLevel toProto(Optional<ThreatLevel> level) {
        return level.map(l -> switch (l) {
            case LOW -> pk.nust.cuas.threat.v1.ThreatLevel.THREAT_LEVEL_LOW;
            case MEDIUM -> pk.nust.cuas.threat.v1.ThreatLevel.THREAT_LEVEL_MEDIUM;
            case HIGH -> pk.nust.cuas.threat.v1.ThreatLevel.THREAT_LEVEL_HIGH;
            case CRITICAL -> pk.nust.cuas.threat.v1.ThreatLevel.THREAT_LEVEL_CRITICAL;
        }).orElse(pk.nust.cuas.threat.v1.ThreatLevel.THREAT_LEVEL_UNSPECIFIED);
    }

    private static pk.nust.cuas.threat.v1.Recommendation toProto(
            pk.nust.cuas.threat.domain.model.Recommendation recommendation) {
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

    private static pk.nust.cuas.threat.v1.ThreatFactorType toProto(ThreatFactorType type) {
        return switch (type) {
            case MODEL_CONFIDENCE ->
                    pk.nust.cuas.threat.v1.ThreatFactorType.THREAT_FACTOR_TYPE_MODEL_CONFIDENCE;
            case PROXIMITY -> pk.nust.cuas.threat.v1.ThreatFactorType.THREAT_FACTOR_TYPE_PROXIMITY;
            case CLOSING_VELOCITY ->
                    pk.nust.cuas.threat.v1.ThreatFactorType.THREAT_FACTOR_TYPE_CLOSING_VELOCITY;
            case TRAJECTORY_INTENT ->
                    pk.nust.cuas.threat.v1.ThreatFactorType.THREAT_FACTOR_TYPE_TRAJECTORY_INTENT;
            case SENSOR_AGREEMENT ->
                    pk.nust.cuas.threat.v1.ThreatFactorType.THREAT_FACTOR_TYPE_SENSOR_AGREEMENT;
        };
    }

    private static AssetApproach toApproach(ClosestPointOfApproach cpa) {
        return new AssetApproach(
                cpa.getAssetId(),
                cpa.getAssetName(),
                cpa.getDistanceM(),
                Math.max(0.0, cpa.getTimeToCpaS()),
                cpa.getClosing());
    }

    /**
     * @param id a track id from a message
     * @return the parsed id, or empty when the message is not for something this service can score.
     *         Reported rather than thrown: a malformed id on a topic is a fact about the message,
     *         and the consumer's job is to skip it, not to fail the partition behind it.
     */
    public static Optional<UUID> parseUuid(String id) {
        try {
            return Optional.of(UUID.fromString(id));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    /**
     * @param observedAt when the observation was made
     * @param modelId    the reporting service
     * @return an observation record
     */
    public InferenceObservation toObservation(String modelId, String modelVersion, String label,
                                              String labelClass, double confidence,
                                              Instant observedAt) {
        return new InferenceObservation(modelId, modelVersion, label, labelClass,
                clamp(confidence), observedAt);
    }

    private static double clamp(double confidence) {
        return Math.max(0.0, Math.min(1.0, confidence));
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
