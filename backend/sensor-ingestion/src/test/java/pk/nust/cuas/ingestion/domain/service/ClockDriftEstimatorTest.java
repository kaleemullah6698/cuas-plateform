package pk.nust.cuas.ingestion.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ClockDriftEstimatorTest {

    @Test
    void firstSampleWithNoHistoryBlendsFromZero() {
        ClockDriftEstimator estimator = new ClockDriftEstimator(0.5d);

        // 0.5 * 1000 + 0.5 * 0
        assertThat(estimator.next(0L, 1000L)).isEqualTo(500L);
    }

    @Test
    void aHigherAlphaTracksTheNewSampleFaster() {
        long previous = 100L;
        long sample = 1100L;

        long steady = new ClockDriftEstimator(0.1d).next(previous, sample);
        long reactive = new ClockDriftEstimator(0.8d).next(previous, sample);

        // 0.1*1100 + 0.9*100 = 200 ; 0.8*1100 + 0.2*100 = 900
        assertThat(steady).isEqualTo(200L);
        assertThat(reactive).isEqualTo(900L);
    }

    @Test
    void repeatedSamplesConvergeTowardsTheTrueOffset() {
        ClockDriftEstimator estimator = new ClockDriftEstimator(0.3d);

        long estimate = 0L;
        for (int i = 0; i < 100; i++) {
            estimate = estimator.next(estimate, 250L);
        }

        // Converges to within a millisecond of the true 250 ms offset. It settles at 249 rather
        // than 250 because the estimate is rounded to whole milliseconds each step: at 249,
        // round(0.3*250 + 0.7*249) = round(249.3) = 249, a stable point one below the true value.
        assertThat(estimate).isBetween(249L, 250L);
    }

    @Test
    void negativeDriftIsHandled() {
        // A sensor clock running ahead of the platform yields a negative offset.
        assertThat(new ClockDriftEstimator(0.5d).next(0L, -800L)).isEqualTo(-400L);
    }

    @Test
    void alphaOutsideTheOpenUnitIntervalIsRejected() {
        assertThatThrownBy(() -> new ClockDriftEstimator(0.0d)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ClockDriftEstimator(1.5d)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void alphaOfOneTakesTheSampleWhole() {
        assertThat(new ClockDriftEstimator(1.0d).next(500L, 900L)).isEqualTo(900L);
    }
}
