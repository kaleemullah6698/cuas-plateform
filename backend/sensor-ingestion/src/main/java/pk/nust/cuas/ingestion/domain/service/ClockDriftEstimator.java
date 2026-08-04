package pk.nust.cuas.ingestion.domain.service;

/**
 * Estimates a sensor's clock drift as an exponentially-weighted moving average (ARCHITECTURE.md
 * sections 4.1.4, 5.3).
 *
 * <p>Each detection yields one sample: {@code ingestTime - sensorTime}, the apparent offset between
 * the sensor's clock and the platform's. A single sample is noisy — it also contains network and
 * queueing latency — so the estimate is smoothed: {@code next = α·sample + (1-α)·previous}. A
 * smaller α trusts history more and reacts slowly; a larger α tracks a genuinely drifting clock
 * faster at the cost of admitting more jitter. The smoothed value is what gets recorded as
 * {@code clock_drift_ms}; the raw {@code sensor_time} is always preserved unchanged (section 5.3).
 *
 * <p>Pure and stateless: the per-sensor running value is held by a {@code ClockDriftStore}, not
 * here, so this can be reasoned about and unit-tested as a function.
 */
public final class ClockDriftEstimator {

    private final double alpha;

    /**
     * @param alpha the smoothing factor in (0,1]; higher tracks faster, lower is steadier
     * @throws IllegalArgumentException when alpha is outside (0,1]
     */
    public ClockDriftEstimator(double alpha) {
        if (!(alpha > 0.0d) || alpha > 1.0d) {
            throw new IllegalArgumentException("alpha must be in (0,1], was " + alpha);
        }
        this.alpha = alpha;
    }

    /**
     * Folds one drift sample into the running estimate.
     *
     * @param previousDriftMs the current estimate, in milliseconds
     * @param sampleDriftMs    the newly observed offset, in milliseconds
     * @return the updated estimate, rounded to the nearest millisecond
     */
    public long next(long previousDriftMs, long sampleDriftMs) {
        double blended = alpha * sampleDriftMs + (1.0d - alpha) * previousDriftMs;
        return Math.round(blended);
    }
}
