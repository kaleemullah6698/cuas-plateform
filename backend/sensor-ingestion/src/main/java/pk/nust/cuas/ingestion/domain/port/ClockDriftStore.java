package pk.nust.cuas.ingestion.domain.port;

/**
 * Holds the running clock-drift estimate for each sensor, between detections.
 *
 * <p>The {@code ClockDriftEstimator} computes the next estimate from the previous one; this port is
 * where the previous one lives. It is kept out of the domain service so the estimator stays a pure
 * function, and behind an interface so the store can be Redis-backed (surviving a partition
 * rebalance) without the domain knowing.
 */
public interface ClockDriftStore {

    /**
     * @param sensorId the sensor
     * @return the last recorded drift estimate in milliseconds, or {@code 0} if none is recorded yet
     */
    long currentDriftMs(String sensorId);

    /**
     * Records the updated drift estimate for a sensor.
     *
     * @param sensorId the sensor
     * @param driftMs  the new estimate in milliseconds
     */
    void update(String sensorId, long driftMs);
}
