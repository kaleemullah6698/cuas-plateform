package pk.nust.cuas.orchestrator.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import pk.nust.cuas.orchestrator.domain.model.InferenceOutcome;
import pk.nust.cuas.orchestrator.domain.model.MockScenario;
import pk.nust.cuas.orchestrator.domain.model.SyntheticOutcome;

class ScenarioLibraryTest {

    private final ScenarioLibrary library = new ScenarioLibrary();

    @Test
    void theSameTrackAlwaysGetsTheSameScenario() {
        // The property the whole mock rests on: a demonstration replays, a failing test reproduces.
        String trackId = "01900000-0000-7000-8000-00000000abcd";

        MockScenario first = library.scenarioFor(trackId);
        for (int i = 0; i < 100; i++) {
            assertThat(library.scenarioFor(trackId)).isEqualTo(first);
        }
    }

    @Test
    void scenariosSpreadAcrossUuidv7IdsRatherThanClustering() {
        // UUIDv7 ids share a long common prefix by construction. A weak hash would map consecutive
        // tracks to one scenario, which would defeat the point of having a library.
        Map<MockScenario, Integer> counts = new EnumMap<>(MockScenario.class);
        for (int i = 0; i < 600; i++) {
            // Realistic v7 shape: a shared time prefix, differing only in the tail.
            String trackId = "01900000-0000-7000-8000-%012x".formatted(i);
            counts.merge(library.scenarioFor(trackId), 1, Integer::sum);
        }

        assertThat(counts).hasSize(MockScenario.values().length);
        assertThat(counts.values()).allSatisfy(count ->
                assertThat(count).as("no scenario should be starved or dominant").isBetween(40, 200));
    }

    @ParameterizedTest
    @EnumSource(MockScenario.class)
    void everyScenarioProducesAWellFormedOutcome(MockScenario scenario) {
        SyntheticOutcome outcome = library.outcomeOf(scenario);

        assertThat(outcome.outcome()).isNotNull();
        assertThat(outcome.confidence()).isBetween(0.0f, 1.0f);
        assertThat(outcome.entropy()).isGreaterThanOrEqualTo(0.0f);
        if (outcome.outcome().isUsable()) {
            assertThat(outcome.predictions()).isNotEmpty();
        }
    }

    @ParameterizedTest
    @EnumSource(MockScenario.class)
    void aScenariosOutcomeIsFixed(MockScenario scenario) {
        assertThat(library.outcomeOf(scenario)).isEqualTo(library.outcomeOf(scenario));
    }

    @Test
    void predictionsAreRankedFromOne() {
        SyntheticOutcome outcome = library.outcomeOf(MockScenario.SWARM);

        assertThat(outcome.predictions()).extracting(p -> p.rank()).containsExactly(1, 2, 3);
        assertThat(outcome.predictions().get(0).confidence())
                .as("rank 1 is the most confident")
                .isGreaterThan(outcome.predictions().get(1).confidence());
    }

    @Test
    void theFalsePositiveScenarioIsConfidentlyNonDrone() {
        // It must be confident, or the operator's dismissal decision is not being exercised.
        SyntheticOutcome outcome = library.outcomeOf(MockScenario.FALSE_POSITIVE_BIRD);

        assertThat(outcome.predictions().get(0).labelClass()).isEqualTo("NON_DRONE");
        assertThat(outcome.confidence()).isGreaterThan(0.8f);
        assertThat(outcome.reliable()).isTrue();
    }

    @Test
    void theDropoutScenarioReportsInsufficientDataWithNoPredictions() {
        SyntheticOutcome outcome = library.outcomeOf(MockScenario.SENSOR_DROPOUT);

        assertThat(outcome.outcome()).isEqualTo(InferenceOutcome.INSUFFICIENT_DATA);
        assertThat(outcome.predictions()).isEmpty();
        assertThat(outcome.errorMessage()).isNotBlank();
    }

    @Test
    void theAmbiguousScenarioIsLowConfidenceHighEntropyAndSelfReportedUnreliable() {
        SyntheticOutcome outcome = library.outcomeOf(MockScenario.LOW_CONFIDENCE_AMBIGUOUS);

        assertThat(outcome.outcome()).isEqualTo(InferenceOutcome.LOW_CONFIDENCE);
        assertThat(outcome.confidence()).isLessThan(0.5f);
        assertThat(outcome.entropy()).isGreaterThan(0.8f);
        assertThat(outcome.reliable()).isFalse();
    }

    @Test
    void theContradictoryScenarioIsUncertainButNotUnreliable() {
        // The model is not malfunctioning; the world is genuinely ambiguous. Different states.
        SyntheticOutcome outcome = library.outcomeOf(MockScenario.CONTRADICTORY_MODALITIES);

        assertThat(outcome.outcome()).isEqualTo(InferenceOutcome.OK);
        assertThat(outcome.reliable()).isTrue();
        assertThat(outcome.entropy()).isGreaterThan(0.9f);
        float top = outcome.predictions().get(0).confidence();
        float second = outcome.predictions().get(1).confidence();
        assertThat(top - second).as("near-equal, which is what makes it contradictory").isLessThan(0.1f);
    }

    @Test
    void differentTracksCanGetDifferentScenarios() {
        long distinct = java.util.stream.Stream.generate(() -> UUID.randomUUID().toString())
                .limit(200)
                .map(library::scenarioFor)
                .distinct()
                .count();

        assertThat(distinct).isEqualTo(MockScenario.values().length);
    }
}
