package pk.nust.cuas.orchestrator.common;

import pk.nust.cuas.inference.v1.InferenceStatus;
import pk.nust.cuas.orchestrator.domain.model.InferenceOutcome;

/**
 * Translates between the domain's {@link InferenceOutcome} and the contract's
 * {@link InferenceStatus} (ARCHITECTURE.md section 6.2).
 *
 * <p>The anti-corruption layer for status, mirroring {@link ModalityMapper} for modality. It keeps
 * the domain free of generated types (CLAUDE.md section 2) and puts the one place that has to know
 * both vocabularies where a contract change is easy to find.
 */
public final class InferenceStatusMapper {

    private InferenceStatusMapper() {
    }

    /**
     * @param outcome a domain outcome
     * @return the wire status
     */
    public static InferenceStatus toProto(InferenceOutcome outcome) {
        return switch (outcome) {
            case OK -> InferenceStatus.STATUS_OK;
            case LOW_CONFIDENCE -> InferenceStatus.STATUS_LOW_CONFIDENCE;
            case INSUFFICIENT_DATA -> InferenceStatus.STATUS_INSUFFICIENT_DATA;
            case MODEL_ERROR -> InferenceStatus.STATUS_MODEL_ERROR;
            case TIMEOUT -> InferenceStatus.STATUS_TIMEOUT;
            case DEGRADED -> InferenceStatus.STATUS_DEGRADED;
        };
    }

    /**
     * @param status a wire status
     * @return the domain outcome; an unspecified or unrecognised status is treated as a model error
     *     rather than as success, because a service that cannot say how it went has not succeeded
     */
    public static InferenceOutcome toDomain(InferenceStatus status) {
        return switch (status) {
            case STATUS_OK -> InferenceOutcome.OK;
            case STATUS_LOW_CONFIDENCE -> InferenceOutcome.LOW_CONFIDENCE;
            case STATUS_INSUFFICIENT_DATA -> InferenceOutcome.INSUFFICIENT_DATA;
            case STATUS_MODEL_ERROR -> InferenceOutcome.MODEL_ERROR;
            case STATUS_TIMEOUT -> InferenceOutcome.TIMEOUT;
            case STATUS_DEGRADED -> InferenceOutcome.DEGRADED;
            case STATUS_UNSPECIFIED, UNRECOGNIZED -> InferenceOutcome.MODEL_ERROR;
        };
    }
}
