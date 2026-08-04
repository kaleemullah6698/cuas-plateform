package pk.nust.cuas.track.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Tunable parameters of track correlation (ARCHITECTURE.md section 4.1.5), bound from the
 * {@code correlation.*} namespace with defaults that make the service runnable with no configuration.
 *
 * @param association the spatio-temporal association gate
 * @param confirmation the tentative → confirmed rule
 * @param sector      the sector grid
 */
@ConfigurationProperties(prefix = "correlation")
public record CorrelationProperties(
        @DefaultValue Association association,
        @DefaultValue Confirmation confirmation,
        @DefaultValue Sector sector) {

    /**
     * @param distanceGateM the maximum separation, in metres, for a detection to extend a track
     * @param timeGate      the maximum time gap between a track's last state and a detection
     */
    public record Association(
            @DefaultValue("500") double distanceGateM,
            @DefaultValue("PT30S") Duration timeGate) {
    }

    /**
     * @param threshold the number of associated detections at which a track is confirmed
     */
    public record Confirmation(@DefaultValue("3") int threshold) {
    }

    /**
     * @param cellDegrees the side of each square sector cell, in degrees
     */
    public record Sector(@DefaultValue("1.0") double cellDegrees) {
    }
}
