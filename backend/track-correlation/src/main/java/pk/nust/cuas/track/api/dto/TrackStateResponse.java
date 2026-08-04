package pk.nust.cuas.track.api.dto;

import java.time.Instant;
import pk.nust.cuas.track.domain.model.TrackState;

/**
 * The wire representation of one observed state in a track's history.
 *
 * @param latitude    latitude at this observation
 * @param longitude   longitude at this observation
 * @param altitudeM   altitude, metres AMSL
 * @param speedMs     ground speed, m/s
 * @param headingDeg  heading, degrees from true north
 * @param observedAt  ingest time of the detection this state came from
 * @param detectionId the detection this state came from
 * @param sensorId    the sensor that reported it
 */
public record TrackStateResponse(
        double latitude,
        double longitude,
        double altitudeM,
        double speedMs,
        double headingDeg,
        Instant observedAt,
        String detectionId,
        String sensorId) {

    public static TrackStateResponse from(TrackState state) {
        return new TrackStateResponse(
                state.position().latitude(),
                state.position().longitude(),
                state.position().altitudeM(),
                state.velocity().speedMs(),
                state.velocity().headingDeg(),
                state.observedAt(),
                state.detectionId(),
                state.sensorId());
    }
}
