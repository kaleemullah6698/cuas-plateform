package pk.nust.cuas.orchestrator.domain.service;

import java.util.List;
import java.util.Map;
import pk.nust.cuas.orchestrator.domain.model.InferenceOutcome;
import pk.nust.cuas.orchestrator.domain.model.MockScenario;
import pk.nust.cuas.orchestrator.domain.model.SyntheticOutcome;
import pk.nust.cuas.orchestrator.domain.model.SyntheticPrediction;

/**
 * Resolves a track to a scenario, and a scenario to a synthetic result (ARCHITECTURE.md section 6.4).
 *
 * <p>Deterministic throughout: the scenario is chosen from the track id, and each scenario's output
 * is fixed. The same track therefore always yields the same prediction, which is what makes a
 * demonstration repeatable and a test reproducible.
 *
 * <p>The labels below are drone and non-drone <em>classes</em> — facts about what is in the air. They
 * are not service or model names, and nothing here knows which model is being stood in for: the same
 * library serves every registered service, because a mock that varied by service would need to know
 * what each service models.
 *
 * <p>Pure and framework-free.
 */
public final class ScenarioLibrary {

    private static final String DRONE = "DRONE";
    private static final String NON_DRONE = "NON_DRONE";
    private static final String BACKGROUND = "BACKGROUND";

    /**
     * Assigns a scenario to a track.
     *
     * @param trackId the track being inferred about
     * @return its scenario, stable for the lifetime of that id
     */
    public MockScenario scenarioFor(String trackId) {
        MockScenario[] scenarios = MockScenario.values();
        long seed = DeterministicSeed.of("scenario", trackId);
        return scenarios[(int) Math.floorMod(seed, scenarios.length)];
    }

    /**
     * The synthetic result a scenario produces.
     *
     * @param scenario the scenario
     * @return its outcome; every field is fixed, so the result is identical on every call
     */
    public SyntheticOutcome outcomeOf(MockScenario scenario) {
        return switch (scenario) {
            case SINGLE_INTRUSION -> SyntheticOutcome.of(
                    List.of(new SyntheticPrediction("quadcopter-small", DRONE, 0.94f, 1,
                                    Map.of("range_m", 640.0f, "bearing_deg", 137.0f)),
                            SyntheticPrediction.of("background", BACKGROUND, 0.04f, 2)),
                    0.94f, 0.21f, true);

            case SWARM -> SyntheticOutcome.of(
                    List.of(new SyntheticPrediction("quadcopter-small", DRONE, 0.91f, 1,
                                    Map.of("range_m", 780.0f, "count", 5.0f)),
                            SyntheticPrediction.of("quadcopter-medium", DRONE, 0.86f, 2),
                            SyntheticPrediction.of("fixed-wing-small", DRONE, 0.72f, 3)),
                    0.91f, 0.38f, true);

            // A *confident* non-drone. The operator must be able to dismiss this, and the confidence
            // has to be high or the dismissal decision is not being exercised realistically.
            case FALSE_POSITIVE_BIRD -> SyntheticOutcome.of(
                    List.of(new SyntheticPrediction("bird", NON_DRONE, 0.89f, 1,
                                    Map.of("range_m", 310.0f, "rcs_m2", 0.01f)),
                            SyntheticPrediction.of("quadcopter-small", DRONE, 0.07f, 2)),
                    0.89f, 0.26f, true);

            case SENSOR_DROPOUT -> SyntheticOutcome.failure(InferenceOutcome.INSUFFICIENT_DATA,
                    "insufficient input to infer from");

            case LOW_CONFIDENCE_AMBIGUOUS -> new SyntheticOutcome(
                    InferenceOutcome.LOW_CONFIDENCE,
                    List.of(SyntheticPrediction.of("unknown", BACKGROUND, 0.34f, 1),
                            SyntheticPrediction.of("quadcopter-small", DRONE, 0.31f, 2)),
                    0.34f, 0.92f, false, "");

            // Two plausible classes at near-equal confidence: the disagreement the dashboard renders
            // as partial sensor agreement (section 2.5.6). Deliberately reliable=true — the model is
            // not malfunctioning, the world is genuinely ambiguous, and those are different states.
            case CONTRADICTORY_MODALITIES -> new SyntheticOutcome(
                    InferenceOutcome.OK,
                    List.of(SyntheticPrediction.of("quadcopter-small", DRONE, 0.52f, 1),
                            SyntheticPrediction.of("bird", NON_DRONE, 0.48f, 2)),
                    0.52f, 0.97f, true, "");
        };
    }
}
