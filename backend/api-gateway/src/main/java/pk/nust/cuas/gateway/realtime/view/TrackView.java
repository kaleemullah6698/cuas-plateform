package pk.nust.cuas.gateway.realtime.view;

/**
 * The projection of a track for the operator console (ARCHITECTURE.md §2.5.1, §3.5). The gateway maps
 * the {@code track.updated.v1} protobuf to this JSON shape — only the fields the thin track list needs,
 * never the full track record.
 *
 * @param trackId        the track
 * @param sectorId       its sector
 * @param status         its lifecycle status (as the contract enum name)
 * @param latitude       decimal degrees, or null if unknown
 * @param longitude      decimal degrees, or null if unknown
 * @param altitudeM      metres, or null if unknown
 * @param speedMs        ground speed, m/s
 * @param headingDeg     heading, degrees
 * @param classLabel     the AI classification label, if the track has one
 * @param confidence     the classification confidence [0,1], or null if unclassified
 * @param observedAt     the time this estimate is valid for (ISO-8601)
 * @param lastSeen       the ingest time of the latest detection (ISO-8601)
 * @param detectionCount how many detections have contributed
 */
public record TrackView(String trackId, String sectorId, String status, Double latitude,
                        Double longitude, Double altitudeM, double speedMs, double headingDeg,
                        String classLabel, Double confidence, String observedAt, String lastSeen,
                        int detectionCount) {
}
