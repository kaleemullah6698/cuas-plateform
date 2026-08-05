package pk.nust.cuas.orchestrator.domain.model;

import java.util.Map;

/**
 * One synthetic classification produced by a scenario (ARCHITECTURE.md section 6.4).
 *
 * <p>The domain-side shape of {@code inference.v1.Prediction}, so scenarios can be written and tested
 * as pure functions and the adapter maps them to the wire type.
 *
 * @param label      the class, e.g. a specific airframe, {@code bird}, {@code background}
 * @param labelClass the coarse grouping the dashboard colours by: {@code DRONE}, {@code NON_DRONE},
 *                   {@code BACKGROUND}
 * @param confidence calibrated, in [0,1]
 * @param rank       1 for the top prediction
 * @param attributes free-form numeric detail, e.g. range, bearing
 */
public record SyntheticPrediction(
        String label, String labelClass, float confidence, int rank, Map<String, Float> attributes) {

    public SyntheticPrediction {
        if (label == null || label.isBlank() || labelClass == null || labelClass.isBlank()) {
            throw new IllegalArgumentException("label and labelClass are required");
        }
        if (confidence < 0.0f || confidence > 1.0f) {
            throw new IllegalArgumentException("confidence must be in [0,1], was " + confidence);
        }
        if (rank < 1) {
            throw new IllegalArgumentException("rank starts at 1, was " + rank);
        }
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    /** A prediction with no extra attributes. */
    public static SyntheticPrediction of(String label, String labelClass, float confidence, int rank) {
        return new SyntheticPrediction(label, labelClass, confidence, rank, Map.of());
    }
}
