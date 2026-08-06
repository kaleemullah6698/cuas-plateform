package pk.nust.cuas.threat.domain.model;

import java.time.Instant;

/**
 * One AI service's verdict on a track, taken from an {@code ai.inference.completed.v1} event.
 *
 * <p>The service is identified by {@code modelId} because that is what the inference contract
 * carries, and because sensor agreement is measured across <em>reporting services</em>: two services
 * that saw the same object through different modalities and reached the same label is the
 * corroboration section 2.4.2 renders as "Fusion agreement: 3 of 3 modalities". This service never
 * branches on which model a {@code modelId} names — it counts and compares, exactly as it would for
 * a model that does not exist yet.
 *
 * @param modelId      the reporting service's model identity
 * @param modelVersion its version; {@code 0.0.0-mock} for a mocked result (section 6.4)
 * @param label        the top prediction
 * @param labelClass   {@code DRONE} | {@code NON_DRONE} | {@code BACKGROUND}
 * @param confidence   calibrated confidence in {@code [0,1]}
 * @param reportedAt   when the result was observed by this service
 */
public record InferenceObservation(
        String modelId,
        String modelVersion,
        String label,
        String labelClass,
        double confidence,
        Instant reportedAt) {

    public InferenceObservation {
        if (modelId == null || modelId.isBlank()) {
            throw new IllegalArgumentException("An inference observation must name its model");
        }
        if (reportedAt == null) {
            throw new IllegalArgumentException("An inference observation must carry its time");
        }
        if (confidence < 0.0 || confidence > 1.0 || Double.isNaN(confidence)) {
            throw new IllegalArgumentException("Confidence must be in [0,1], was " + confidence);
        }
        modelVersion = modelVersion == null ? "" : modelVersion;
        label = label == null ? "" : label;
        labelClass = labelClass == null ? "" : labelClass;
    }

    /**
     * @return {@code true} when this result was produced by a mock provider rather than a trained
     *         model (section 6.4). Not currently a scoring input — it is carried so the breakdown an
     *         operator reads can say so, since a score built entirely on mocked inference is a very
     *         different claim from one built on trained models.
     */
    public boolean isMocked() {
        return modelVersion.endsWith("-mock");
    }
}
