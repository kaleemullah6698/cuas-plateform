package pk.nust.cuas.orchestrator.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import pk.nust.cuas.orchestrator.domain.model.InferenceOutcome;

class FailureInjectorTest {

    private static long countOf(FailureInjector injector, InferenceOutcome wanted, int samples) {
        long count = 0;
        for (int i = 0; i < samples; i++) {
            if (injector.inject("track-" + i, "some-service").filter(wanted::equals).isPresent()) {
                count++;
            }
        }
        return count;
    }

    @Test
    void disabledInjectionNeverFails() {
        FailureInjector injector = FailureInjector.disabled();

        assertThat(injector.isEnabled()).isFalse();
        for (int i = 0; i < 500; i++) {
            assertThat(injector.inject("track-" + i, "svc")).isEmpty();
        }
    }

    @Test
    void theSameTrackAlwaysFailsTheSameWay() {
        // A failure that "happens sometimes" cannot be demonstrated or debugged. java.util.Random
        // here would make every injected failure unrepeatable.
        FailureInjector injector = new FailureInjector(0.3d, 0.3d, 0.2d);

        Optional<InferenceOutcome> first = injector.inject("track-a", "svc");
        for (int i = 0; i < 100; i++) {
            assertThat(injector.inject("track-a", "svc")).isEqualTo(first);
        }
    }

    @Test
    void theConfiguredTimeoutRateIsObservedAcrossManyTracks() {
        FailureInjector injector = new FailureInjector(0.25d, 0.0d, 0.0d);

        long timeouts = countOf(injector, InferenceOutcome.TIMEOUT, 4000);

        assertThat(timeouts / 4000.0d).isBetween(0.21d, 0.29d);
    }

    @Test
    void theThreeRatesComposeRatherThanOverlap() {
        // Drawn from disjoint bands of one value, so 10% + 10% + 10% is 30% failure overall — not
        // the 27% that three independent tests would give.
        FailureInjector injector = new FailureInjector(0.1d, 0.1d, 0.1d);

        long failures = 0;
        for (int i = 0; i < 4000; i++) {
            if (injector.inject("track-" + i, "svc").isPresent()) {
                failures++;
            }
        }

        assertThat(failures / 4000.0d).isBetween(0.27d, 0.33d);
    }

    @Test
    void eachFailureKindIsReachable() {
        FailureInjector injector = new FailureInjector(0.2d, 0.2d, 0.2d);

        assertThat(countOf(injector, InferenceOutcome.TIMEOUT, 2000)).isPositive();
        assertThat(countOf(injector, InferenceOutcome.MODEL_ERROR, 2000)).isPositive();
        assertThat(countOf(injector, InferenceOutcome.LOW_CONFIDENCE, 2000)).isPositive();
    }

    @Test
    void onlyTheTimeoutKindCountsAsATransportFailure() {
        // What the circuit breaker is allowed to act on. A model error is the model's answer, not
        // evidence the service is unreachable.
        assertThat(InferenceOutcome.TIMEOUT.isTransportFailure()).isTrue();
        assertThat(InferenceOutcome.MODEL_ERROR.isTransportFailure()).isFalse();
        assertThat(InferenceOutcome.LOW_CONFIDENCE.isTransportFailure()).isFalse();
        assertThat(InferenceOutcome.INSUFFICIENT_DATA.isTransportFailure()).isFalse();
    }

    @Test
    void aRateOutsideZeroToOneIsRejected() {
        assertThatThrownBy(() -> new FailureInjector(-0.1d, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FailureInjector(1.1d, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ratesSummingAboveOneAreRejected() {
        // They would leave no successful calls at all, which is certainly a configuration mistake.
        assertThatThrownBy(() -> new FailureInjector(0.5d, 0.4d, 0.3d))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no successful calls");
    }
}
