package pk.nust.cuas.orchestrator.domain.model;

import java.util.List;

/**
 * What a scenario decided, before it becomes a wire response (ARCHITECTURE.md section 6.4).
 *
 * @param outcome         the status this inference reports
 * @param predictions     the synthetic classifications, ranked; empty for a failure outcome
 * @param confidence      the overall calibrated score in [0,1]
 * @param entropy         predictive uncertainty — high when the scenario is deliberately ambiguous
 * @param reliable        the service's own judgement of its output, which an ambiguous scenario sets
 *                        false so the dashboard's degraded states are exercised before a real model
 *                        can produce one
 * @param errorMessage    populated only for a failure outcome
 */
public record SyntheticOutcome(
        InferenceOutcome outcome,
        List<SyntheticPrediction> predictions,
        float confidence,
        float entropy,
        boolean reliable,
        String errorMessage) {

    public SyntheticOutcome {
        if (outcome == null) {
            throw new IllegalArgumentException("an outcome is required");
        }
        predictions = predictions == null ? List.of() : List.copyOf(predictions);
        errorMessage = errorMessage == null ? "" : errorMessage;
    }

    /** A successful outcome carrying ranked predictions. */
    public static SyntheticOutcome of(List<SyntheticPrediction> predictions, float confidence,
                                      float entropy, boolean reliable) {
        return new SyntheticOutcome(InferenceOutcome.OK, predictions, confidence, entropy, reliable, "");
    }

    /** A failure outcome: no predictions, and a reason. */
    public static SyntheticOutcome failure(InferenceOutcome outcome, String message) {
        return new SyntheticOutcome(outcome, List.of(), 0.0f, 0.0f, false, message);
    }
}
