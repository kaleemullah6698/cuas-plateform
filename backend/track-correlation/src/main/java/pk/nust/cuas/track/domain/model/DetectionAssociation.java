package pk.nust.cuas.track.domain.model;

import java.time.Instant;
import java.util.OptionalDouble;

/**
 * The record that a detection was associated to a track, and how close it was to the track's state
 * at the time (ARCHITECTURE.md section 4.1.5, {@code track_detections}).
 *
 * <p>The distance is empty when the association created the track — there was no prior state to
 * measure against. It is present, and equals the gate distance, when the detection extended an
 * existing track. This is the audit of how a track was built, and the idempotency key: a detection
 * associates to a track at most once.
 *
 * @param detectionId  the associated detection
 * @param sensorId     the reporting sensor
 * @param sensorSeq    the sensor sequence number
 * @param distanceM    the gate distance in metres, or empty when the track was created
 * @param associatedAt when the association was made
 */
public record DetectionAssociation(
        String detectionId,
        String sensorId,
        long sensorSeq,
        OptionalDouble distanceM,
        Instant associatedAt) {

    public DetectionAssociation {
        if (distanceM == null) {
            throw new IllegalArgumentException("distanceM must not be null; use OptionalDouble.empty()");
        }
    }

    /** The association that created a track, with no prior state to measure a distance against. */
    public static DetectionAssociation onCreation(String detectionId, String sensorId, long sensorSeq,
                                                  Instant at) {
        return new DetectionAssociation(detectionId, sensorId, sensorSeq, OptionalDouble.empty(), at);
    }

    /** The association that extended a track, gated at {@code distanceM} metres. */
    public static DetectionAssociation onUpdate(String detectionId, String sensorId, long sensorSeq,
                                                double distanceM, Instant at) {
        return new DetectionAssociation(detectionId, sensorId, sensorSeq, OptionalDouble.of(distanceM), at);
    }
}
