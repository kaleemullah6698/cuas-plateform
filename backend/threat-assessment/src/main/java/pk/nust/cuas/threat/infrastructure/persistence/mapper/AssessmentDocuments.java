package pk.nust.cuas.threat.infrastructure.persistence.mapper;

import java.util.List;

/**
 * The stored shapes of an assessment's two JSONB columns.
 *
 * <p>Separate types from the domain models, mapped explicitly by {@link AssessmentJsonCodec}. The
 * duplication is deliberate and is the same rule the platform applies between domain models and JPA
 * entities: a domain record used directly as a persistence format makes every refactoring of the
 * domain a silent migration of data already written. These records change only when the stored
 * format is meant to change.
 *
 * <p>Absence is a {@code null} field rather than an {@link java.util.Optional}. JSON has no notion
 * of an empty optional, and serialising one produces {@code {"present":false}} or a bare {@code null}
 * depending on which modules happen to be registered — a persistence format that depends on
 * ObjectMapper configuration is one that breaks when an unrelated dependency changes.
 */
final class AssessmentDocuments {

    private AssessmentDocuments() {
    }

    /**
     * The per-factor breakdown, as stored.
     *
     * @param factors one row per factor the policy defined
     */
    record FactorsDocument(List<FactorRow> factors) {
    }

    /**
     * @param type              the factor name
     * @param value             the normalised value, or null when the factor was unavailable
     * @param weight            the policy weight it carried
     * @param unavailableReason why it was unavailable, or null when it was not
     */
    record FactorRow(String type, Double value, double weight, String unavailableReason) {
    }

    /**
     * The scoring inputs, as stored — what makes a past assessment reproducible.
     *
     * @param snapshot                the track facts, or null before any track update arrived
     * @param observations            the latest result from each reporting service
     * @param consecutiveClosingCount the closing streak behind the trajectory-intent factor
     */
    record EvidenceDocument(
            SnapshotRow snapshot,
            List<ObservationRow> observations,
            int consecutiveClosingCount) {
    }

    /**
     * @param trackId          the track
     * @param sectorId         its sector
     * @param observedAtMillis the estimate time, as epoch milliseconds
     * @param speedMs          ground speed
     * @param approach         the nearest asset's CPA, or null when there was none
     * @param correlationId    end-to-end trace
     */
    record SnapshotRow(
            String trackId,
            String sectorId,
            long observedAtMillis,
            double speedMs,
            ApproachRow approach,
            String correlationId) {
    }

    /**
     * @param assetId    the protected asset
     * @param assetName  what the operator sees
     * @param distanceM  separation at the closest point
     * @param timeToCpaS seconds until that point
     * @param closing    false when already receding
     */
    record ApproachRow(
            String assetId,
            String assetName,
            double distanceM,
            double timeToCpaS,
            boolean closing) {
    }

    /**
     * @param modelId          the reporting service
     * @param modelVersion     its version
     * @param label            the top prediction
     * @param labelClass       the prediction's class
     * @param confidence       calibrated confidence
     * @param reportedAtMillis when the result was observed, as epoch milliseconds
     */
    record ObservationRow(
            String modelId,
            String modelVersion,
            String label,
            String labelClass,
            double confidence,
            long reportedAtMillis) {
    }
}
