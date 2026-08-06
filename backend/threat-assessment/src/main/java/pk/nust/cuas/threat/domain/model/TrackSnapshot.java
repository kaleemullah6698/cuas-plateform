package pk.nust.cuas.threat.domain.model;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * The facts about a track that scoring needs, taken from one {@code track.updated.v1} event.
 *
 * <p>The domain's own view of a track, not the wire type. Only what section 6.3.4's factors are
 * computed from is carried: the identity, the observation time, the ground speed, and the approach
 * geometry. The rest of the track — its Kalman covariance, its detection history, its predicted
 * trajectory — is real and useful, and none of it is scoring input, so admitting it here would make
 * this service's contract with track-correlation wider than its need.
 *
 * @param trackId       the track being scored
 * @param sectorId      the sector it belongs to
 * @param observedAt    the time the track's estimate is valid for — <em>not</em> its last-seen
 *                      time; section 5.3.2 records why those two clocks diverge while coasting
 * @param speedMs       estimated ground speed
 * @param approach      the nearest protected asset's CPA, or empty when no asset is configured or
 *                      none is reachable within the prediction horizon
 * @param correlationId the end-to-end trace carried from the track event
 */
public record TrackSnapshot(
        UUID trackId,
        String sectorId,
        Instant observedAt,
        double speedMs,
        Optional<AssetApproach> approach,
        String correlationId) {

    public TrackSnapshot {
        if (trackId == null || observedAt == null || approach == null) {
            throw new IllegalArgumentException("A track snapshot needs an id, a time and an approach");
        }
        if (speedMs < 0.0 || Double.isNaN(speedMs)) {
            throw new IllegalArgumentException("Ground speed must be non-negative, was " + speedMs);
        }
        sectorId = sectorId == null ? "" : sectorId;
        correlationId = correlationId == null ? "" : correlationId;
    }

    /** @return {@code true} when the track is approaching a protected asset */
    public boolean isClosing() {
        return approach.map(AssetApproach::closing).orElse(false);
    }
}
