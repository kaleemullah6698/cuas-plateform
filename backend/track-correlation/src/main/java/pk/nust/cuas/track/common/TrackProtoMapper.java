package pk.nust.cuas.track.common;

import com.google.protobuf.Timestamp;
import java.time.Instant;
import pk.nust.cuas.commons.correlation.CorrelationId;
import pk.nust.cuas.detection.v1.GeoPosition;
import pk.nust.cuas.track.domain.model.GeoPoint;
import pk.nust.cuas.track.domain.model.PredictedPoint;
import pk.nust.cuas.track.domain.model.Track;
import pk.nust.cuas.track.domain.model.TrackOutlook;
import pk.nust.cuas.track.domain.model.TrackStatus;
import pk.nust.cuas.track.domain.service.KalmanFilter;
import pk.nust.cuas.track.v1.ClosestPointOfApproach;
import pk.nust.cuas.track.v1.KinematicEstimate;

/**
 * Maps the domain {@link Track} to the {@code track.v1.Track} wire contract (ARCHITECTURE.md section
 * 5.3.2).
 *
 * <p>In {@code common/} because both adapters that emit a track need it — the Kafka producer in
 * {@code infrastructure/} and the gRPC stream in {@code api/} — and CLAUDE.md section 2 forbids
 * either of those packages from importing the other. Duplicating the mapping instead would let the
 * two surfaces drift, so a track would describe itself differently depending on which one a
 * consumer read it from.
 *
 * <p>Stateless apart from the filter it reads uncertainties through, which is why it is a singleton
 * component rather than a static helper: {@link KalmanFilter} is configured tuning, not a constant.
 */
public class TrackProtoMapper {

    private static final String SCHEMA_VERSION = "1.0.0";

    private final KalmanFilter kalman;

    public TrackProtoMapper(KalmanFilter kalman) {
        this.kalman = kalman;
    }

    /**
     * @param track   the track to describe
     * @param outlook its trajectory and CPA; {@link TrackOutlook#none()} carries neither
     * @return the wire representation
     */
    public pk.nust.cuas.track.v1.Track toProto(Track track, TrackOutlook outlook) {
        var estimate = track.estimate();
        var builder = pk.nust.cuas.track.v1.Track.newBuilder()
                .setTrackId(track.id().toString())
                .setSectorId(track.sector().value())
                .setStatus(toProtoStatus(track.status()))
                .setPosition(toProtoPosition(estimate.position()))
                .setSpeedMs(estimate.velocity().speedMs())
                .setHeadingDeg(estimate.velocity().headingDeg())
                .setObservedAt(toTimestamp(estimate.observedAt()))
                .setEstimate(KinematicEstimate.newBuilder()
                        .setVerticalSpeedMs(kalman.verticalSpeedMs(track.filter()))
                        .setAccelerationMs2(kalman.accelerationMs2(track.filter()))
                        .setPositionUncertaintyM(kalman.positionUncertaintyM(track.filter()))
                        .setVelocityUncertaintyMs(kalman.velocityUncertaintyMs(track.filter())))
                .setFirstSeen(toTimestamp(track.firstSeen()))
                .setLastSeen(toTimestamp(track.lastDetectionAt()))
                .setDetectionCount(track.detectionCount())
                .setLastDetectionId(estimate.detectionId())
                .setSchemaVersion(SCHEMA_VERSION)
                .setCorrelationId(CorrelationId.current().orElse(""));

        track.mergedInto().ifPresent(survivor -> builder.setMergedIntoTrackId(survivor.toString()));

        for (PredictedPoint point : outlook.predictedTrajectory()) {
            builder.addPredictedTrajectory(pk.nust.cuas.track.v1.PredictedPoint.newBuilder()
                    .setPosition(toProtoPosition(point.position()))
                    .setAt(toTimestamp(point.at()))
                    .setUncertaintyM(point.uncertaintyM()));
        }
        outlook.nearestAssetCpa().ifPresent(cpa -> builder.setNearestAssetCpa(
                ClosestPointOfApproach.newBuilder()
                        .setAssetId(cpa.asset().assetId())
                        .setAssetName(cpa.asset().name())
                        .setDistanceM(cpa.distanceM())
                        .setTimeToCpaS(cpa.timeToCpaS())
                        .setAt(toTimestamp(cpa.at()))
                        .setClosing(cpa.closing())));
        return builder.build();
    }

    private static GeoPosition.Builder toProtoPosition(GeoPoint point) {
        return GeoPosition.newBuilder()
                .setLatitude(point.latitude())
                .setLongitude(point.longitude())
                .setAltitudeM(point.altitudeM());
    }

    private static pk.nust.cuas.track.v1.TrackStatus toProtoStatus(TrackStatus status) {
        return switch (status) {
            case TENTATIVE -> pk.nust.cuas.track.v1.TrackStatus.TRACK_STATUS_TENTATIVE;
            case CONFIRMED -> pk.nust.cuas.track.v1.TrackStatus.TRACK_STATUS_CONFIRMED;
            case COASTING -> pk.nust.cuas.track.v1.TrackStatus.TRACK_STATUS_COASTING;
            case LOST -> pk.nust.cuas.track.v1.TrackStatus.TRACK_STATUS_LOST;
            case MERGED -> pk.nust.cuas.track.v1.TrackStatus.TRACK_STATUS_MERGED;
        };
    }

    private static Timestamp toTimestamp(Instant instant) {
        return Timestamp.newBuilder()
                .setSeconds(instant.getEpochSecond()).setNanos(instant.getNano()).build();
    }
}
