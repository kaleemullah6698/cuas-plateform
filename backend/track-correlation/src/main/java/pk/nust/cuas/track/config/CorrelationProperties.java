package pk.nust.cuas.track.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Tunable parameters of track correlation (ARCHITECTURE.md section 4.1.5), bound from the
 * {@code correlation.*} namespace with defaults that make the service runnable with no configuration.
 *
 * @param association the spatio-temporal association gate
 * @param confirmation the tentative → confirmed rule
 * @param sector      the sector grid
 * @param kalman      the Kalman filter tuning
 * @param lifecycle   the miss-timeout lifecycle
 * @param prediction  the trajectory-prediction horizon
 * @param merge       the cross-sensor merge rule
 * @param assets      the protected assets CPA is measured against
 */
@ConfigurationProperties(prefix = "correlation")
public record CorrelationProperties(
        @DefaultValue Association association,
        @DefaultValue Confirmation confirmation,
        @DefaultValue Sector sector,
        @DefaultValue Kalman kalman,
        @DefaultValue Lifecycle lifecycle,
        @DefaultValue Prediction prediction,
        @DefaultValue Merge merge,
        @DefaultValue List<Asset> assets) {

    /**
     * @param distanceGateM the maximum separation, in metres, between a detection and a track's
     *                      predicted position for the detection to extend that track
     */
    public record Association(@DefaultValue("500") double distanceGateM) {
    }

    /** @param threshold the number of associated detections at which a track is confirmed */
    public record Confirmation(@DefaultValue("3") int threshold) {
    }

    /** @param cellDegrees the side of each square sector cell, in degrees */
    public record Sector(@DefaultValue("1.0") double cellDegrees) {
    }

    /**
     * @param processNoise                 white-noise-jerk process noise (q); higher tracks manoeuvre faster
     * @param measurementNoise             position measurement variance (r), m²; 25 ≈ a 5 m std error
     * @param initialPositionVariance      initial position uncertainty, m²
     * @param initialVelocityVariance      initial velocity uncertainty, (m/s)²
     * @param initialAccelerationVariance  initial acceleration uncertainty, (m/s²)²
     */
    public record Kalman(
            @DefaultValue("1.0") double processNoise,
            @DefaultValue("25.0") double measurementNoise,
            @DefaultValue("100.0") double initialPositionVariance,
            @DefaultValue("100.0") double initialVelocityVariance,
            @DefaultValue("10.0") double initialAccelerationVariance) {
    }

    /**
     * @param coastTimeout  how long a confirmed track may miss detections before it coasts
     * @param lostTimeout   how long any open track may miss detections before it is lost
     * @param checkInterval how often the timeout sweep runs
     */
    public record Lifecycle(
            @DefaultValue("PT10S") Duration coastTimeout,
            @DefaultValue("PT30S") Duration lostTimeout,
            @DefaultValue("PT5S") Duration checkInterval) {
    }

    /**
     * @param horizon how far ahead a track's trajectory is projected
     * @param step    the interval between predicted points
     */
    public record Prediction(
            @DefaultValue("PT30S") Duration horizon,
            @DefaultValue("PT5S") Duration step) {
    }

    /**
     * @param gateM                     how close two tracks' estimates must be to be merge candidates
     * @param requiredConsecutiveSweeps how many sweeps in a row they must stay that close
     * @param checkInterval             how often the merge sweep runs — the unit the sweeps are counted in
     */
    public record Merge(
            @DefaultValue("150") double gateM,
            @DefaultValue("3") int requiredConsecutiveSweeps,
            @DefaultValue("PT5S") Duration checkInterval) {
    }

    /**
     * A protected asset, as configured until the Configuration Service owns them (section 4.1.12).
     *
     * @param id        stable identifier
     * @param name      what the operator sees
     * @param latitude  degrees, WGS-84
     * @param longitude degrees, WGS-84
     * @param altitudeM metres above mean sea level
     */
    public record Asset(
            String id,
            String name,
            double latitude,
            double longitude,
            @DefaultValue("0") double altitudeM) {
    }
}
