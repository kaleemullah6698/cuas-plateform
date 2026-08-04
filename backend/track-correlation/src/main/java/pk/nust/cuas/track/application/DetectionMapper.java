package pk.nust.cuas.track.application;

import com.google.protobuf.Timestamp;
import java.time.Instant;
import java.util.Optional;
import pk.nust.cuas.detection.v1.DetectionRecord;
import pk.nust.cuas.track.domain.model.GeoPoint;
import pk.nust.cuas.track.domain.model.TrackState;
import pk.nust.cuas.track.domain.model.Velocity;

/**
 * Projects a canonical {@code DetectionRecord} (ARCHITECTURE.md section 5.3) into the
 * {@link IncomingDetection} the correlation domain works with — the anti-corruption boundary that
 * keeps the domain free of the generated protobuf types.
 *
 * <p>A detection is usable for tracking only if it has a position to associate on and a time to
 * order it by; one without either is not track-able in the foundation and maps to empty, for the
 * caller to skip. The ordering time is the ingest time — the platform's authoritative clock
 * (section 5.3) — falling back to sensor time only if a record somehow lacks it.
 */
final class DetectionMapper {

    private DetectionMapper() {
    }

    static Optional<IncomingDetection> toIncoming(DetectionRecord record) {
        if (!record.hasPosition() || record.getDetectionId().isBlank()) {
            return Optional.empty();
        }
        Optional<Instant> observedAt = observedAt(record);
        if (observedAt.isEmpty()) {
            return Optional.empty();
        }

        GeoPoint position = new GeoPoint(
                record.getPosition().getLatitude(),
                record.getPosition().getLongitude(),
                record.getPosition().getAltitudeM());
        Velocity velocity = record.hasKinematics()
                ? new Velocity(record.getKinematics().getSpeedMs(), record.getKinematics().getHeadingDeg())
                : Velocity.NONE;
        TrackState state = new TrackState(
                position, velocity, observedAt.get(), record.getDetectionId(), record.getSensorId());
        return Optional.of(new IncomingDetection(state, record.getSensorSeq(), record.getCorrelationId()));
    }

    private static Optional<Instant> observedAt(DetectionRecord record) {
        if (record.hasIngestTime()) {
            return Optional.of(toInstant(record.getIngestTime()));
        }
        if (record.hasSensorTime()) {
            return Optional.of(toInstant(record.getSensorTime()));
        }
        return Optional.empty();
    }

    private static Instant toInstant(Timestamp timestamp) {
        return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
    }
}
