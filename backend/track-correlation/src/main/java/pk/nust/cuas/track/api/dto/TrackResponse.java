package pk.nust.cuas.track.api.dto;

import java.time.Instant;
import pk.nust.cuas.track.domain.model.Track;

/**
 * The wire representation of a track for the read API. A distinct type from the domain {@link Track}
 * (CLAUDE.md section 2); the domain never crosses the API boundary.
 *
 * @param trackId         the track id
 * @param sectorId        the sector it belongs to
 * @param status          {@code TENTATIVE} or {@code CONFIRMED}
 * @param latitude        current latitude
 * @param longitude       current longitude
 * @param altitudeM       current altitude, metres AMSL
 * @param speedMs         current ground speed, m/s
 * @param headingDeg      current heading, degrees from true north
 * @param observedAt      ingest time of the latest associated detection
 * @param firstSeen       ingest time of the first detection
 * @param lastSeen        ingest time of the latest detection
 * @param detectionCount  detections associated so far
 * @param lastDetectionId the latest associated detection
 */
public record TrackResponse(
        String trackId,
        String sectorId,
        String status,
        double latitude,
        double longitude,
        double altitudeM,
        double speedMs,
        double headingDeg,
        Instant observedAt,
        Instant firstSeen,
        Instant lastSeen,
        int detectionCount,
        String lastDetectionId) {

    public static TrackResponse from(Track track) {
        var state = track.state();
        return new TrackResponse(
                track.id().toString(),
                track.sector().value(),
                track.status().name(),
                state.position().latitude(),
                state.position().longitude(),
                state.position().altitudeM(),
                state.velocity().speedMs(),
                state.velocity().headingDeg(),
                state.observedAt(),
                track.firstSeen(),
                track.lastSeen(),
                track.detectionCount(),
                state.detectionId());
    }
}
