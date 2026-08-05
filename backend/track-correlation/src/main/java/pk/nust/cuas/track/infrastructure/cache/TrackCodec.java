package pk.nust.cuas.track.infrastructure.cache;

import java.time.Instant;
import java.util.UUID;
import pk.nust.cuas.track.domain.model.GeoPoint;
import pk.nust.cuas.track.domain.model.KalmanState;
import pk.nust.cuas.track.domain.model.SectorId;
import pk.nust.cuas.track.domain.model.Track;
import pk.nust.cuas.track.domain.model.TrackState;
import pk.nust.cuas.track.domain.model.TrackStatus;
import pk.nust.cuas.track.domain.model.Velocity;
import pk.nust.cuas.track.infrastructure.persistence.KalmanStateCodec;

/**
 * Serialises a {@link Track} to and from its Redis representation.
 *
 * <p>A tab-separated record with a version tag. Tab-separated rather than comma, because the encoded
 * Kalman state is itself a comma-separated list and nesting one delimiter inside another is how
 * codecs quietly corrupt data; a detection id containing a comma would do the same. Tabs cannot
 * appear in any of these fields.
 *
 * <p>The format is versioned and a decode failure is fatal to that <em>entry</em> only — the store
 * treats an unreadable entry as a miss, so a format change deployed mid-flight degrades to database
 * reads rather than to wrong tracks. That is why this is not Java serialization or a schema-less
 * JSON blob: both would fail later and less predictably.
 */
final class TrackCodec {

    private static final String VERSION = "v1";
    private static final String SEPARATOR = "\t";
    private static final int FIELDS = 17;

    private TrackCodec() {
    }

    static String encode(Track track) {
        TrackState estimate = track.estimate();
        GeoPoint position = estimate.position();
        return String.join(SEPARATOR,
                VERSION,
                track.id().toString(),
                track.sector().value(),
                track.status().name(),
                Double.toString(position.latitude()),
                Double.toString(position.longitude()),
                Double.toString(position.altitudeM()),
                Double.toString(estimate.velocity().speedMs()),
                Double.toString(estimate.velocity().headingDeg()),
                estimate.observedAt().toString(),
                estimate.detectionId(),
                estimate.sensorId(),
                Integer.toString(track.detectionCount()),
                track.firstSeen().toString(),
                track.lastDetectionAt().toString(),
                track.mergedInto().map(UUID::toString).orElse(""),
                KalmanStateCodec.encode(track.filter()));
    }

    /**
     * @param encoded a cached entry
     * @return the track it represents
     * @throws IllegalArgumentException when the entry is not a readable v1 record; the store treats
     *     that as a miss
     */
    static Track decode(String encoded) {
        String[] f = encoded.split(SEPARATOR, -1);
        if (f.length != FIELDS || !VERSION.equals(f[0])) {
            throw new IllegalArgumentException("unrecognised cached track format");
        }
        KalmanState filter = KalmanStateCodec.decode(f[16]);
        TrackState estimate = new TrackState(
                new GeoPoint(Double.parseDouble(f[4]), Double.parseDouble(f[5]), Double.parseDouble(f[6])),
                new Velocity(Double.parseDouble(f[7]), Double.parseDouble(f[8])),
                Instant.parse(f[9]), f[10], f[11]);
        UUID id = UUID.fromString(f[1]);
        SectorId sector = new SectorId(f[2]);
        TrackStatus status = TrackStatus.valueOf(f[3]);
        int detectionCount = Integer.parseInt(f[12]);
        Instant firstSeen = Instant.parse(f[13]);
        Instant lastDetectionAt = Instant.parse(f[14]);

        if (status == TrackStatus.MERGED) {
            return Track.reconstituteMerged(id, sector, filter, estimate, detectionCount, firstSeen,
                    lastDetectionAt, UUID.fromString(f[15]));
        }
        return Track.reconstitute(id, sector, status, filter, estimate, detectionCount, firstSeen,
                lastDetectionAt);
    }
}
