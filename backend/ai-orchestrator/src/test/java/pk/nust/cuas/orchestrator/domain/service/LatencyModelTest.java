package pk.nust.cuas.orchestrator.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class LatencyModelTest {

    private final LatencyModel model = new LatencyModel(0.35d);
    private static final Duration BUDGET = Duration.ofMillis(100);

    private List<Long> sampleMany(int n) {
        List<Long> millis = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            millis.add(model.sample(BUDGET, "track-" + i, "some-service").toMillis());
        }
        return millis;
    }

    @Test
    void theSameTrackAndServiceAlwaysGiveTheSameLatency() {
        Duration first = model.sample(BUDGET, "track-a", "service-a");

        for (int i = 0; i < 50; i++) {
            assertThat(model.sample(BUDGET, "track-a", "service-a")).isEqualTo(first);
        }
    }

    @Test
    void theSameTrackOnADifferentServiceGivesADifferentLatency() {
        assertThat(model.sample(BUDGET, "track-a", "service-a"))
                .isNotEqualTo(model.sample(BUDGET, "track-a", "service-b"));
    }

    @Test
    void theNinetyFifthPercentileLandsNearTheConfiguredBudget() {
        // The whole point of fitting the distribution: a service's timeout is hit occasionally but
        // not usually, which is the condition a circuit breaker needs to be exercised against.
        List<Long> millis = sampleMany(2000);
        Collections.sort(millis);
        long p95 = millis.get((int) (millis.size() * 0.95));

        assertThat(p95).isBetween(80L, 130L);
    }

    @Test
    void theMedianSitsWellBelowTheBudget() {
        // A constant-latency mock would make every p95 dashboard and latency assertion meaningless.
        List<Long> millis = sampleMany(2000);
        Collections.sort(millis);
        long median = millis.get(millis.size() / 2);

        assertThat(median).isBetween(40L, 80L);
    }

    @Test
    void theDistributionHasARightTail() {
        // Real inference latency is skewed by queueing and batching; a symmetric spread would not be.
        List<Long> millis = sampleMany(2000);
        Collections.sort(millis);
        long median = millis.get(millis.size() / 2);
        long max = millis.get(millis.size() - 1);

        assertThat(max).as("the tail reaches well past the median").isGreaterThan(median * 2);
    }

    @Test
    void everySampleIsPositive() {
        assertThat(sampleMany(500)).allSatisfy(ms -> assertThat(ms).isPositive());
    }

    @Test
    void aVeryShortBudgetStillProducesAUsableLatency() {
        assertThat(model.sample(Duration.ofMillis(1), "track-a", "svc").toNanos()).isPositive();
    }

    @Test
    void aNonPositiveSigmaIsRejected() {
        assertThatThrownBy(() -> new LatencyModel(0.0d)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LatencyModel(-1.0d)).isInstanceOf(IllegalArgumentException.class);
    }
}
