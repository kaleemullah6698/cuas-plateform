package pk.nust.cuas.track.domain.model;

import java.time.Instant;

/**
 * One observed state of a track: where it was, how it was moving, when, and which detection said so.
 *
 * <p>A track's history is a sequence of these. In the foundation each state is the position and
 * velocity of a single associated detection; in M5.2 it becomes the filtered estimate. {@code
 * observedAt} is the detection's ingest time — the platform's authoritative ordering clock
 * (ARCHITECTURE.md section 5.3) — so states order deterministically regardless of sensor-clock drift.
 *
 * @param position    where the track was
 * @param velocity    how it was moving
 * @param observedAt  the ingest time of the detection this state came from
 * @param detectionId the detection this state came from
 * @param sensorId    the sensor that reported it
 */
public record TrackState(
        GeoPoint position,
        Velocity velocity,
        Instant observedAt,
        String detectionId,
        String sensorId) {

    public TrackState {
        if (position == null || velocity == null || observedAt == null) {
            throw new IllegalArgumentException("position, velocity, and observedAt are required");
        }
        if (detectionId == null || detectionId.isBlank()) {
            throw new IllegalArgumentException("detectionId is required");
        }
        if (sensorId == null || sensorId.isBlank()) {
            throw new IllegalArgumentException("sensorId is required");
        }
    }
}
