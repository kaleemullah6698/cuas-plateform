package pk.nust.cuas.track.infrastructure.messaging.producer;

import com.google.protobuf.Timestamp;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import pk.nust.cuas.commons.correlation.CorrelationId;
import pk.nust.cuas.detection.v1.GeoPosition;
import pk.nust.cuas.track.application.port.TrackEventPublisher;
import pk.nust.cuas.track.domain.model.GeoPoint;
import pk.nust.cuas.track.domain.model.PredictedPoint;
import pk.nust.cuas.track.domain.model.Track;
import pk.nust.cuas.track.domain.model.TrackOutlook;
import pk.nust.cuas.track.domain.model.TrackStatus;
import pk.nust.cuas.track.domain.service.KalmanFilter;
import pk.nust.cuas.track.infrastructure.messaging.TrackTopics;
import pk.nust.cuas.track.v1.ClosestPointOfApproach;
import pk.nust.cuas.track.v1.KinematicEstimate;

/**
 * Kafka adapter publishing track lifecycle events (ARCHITECTURE.md sections 4.1.5, 5.2, 5.3.2).
 *
 * <p>Maps the domain {@link Track} to the {@code track.v1.Track} contract and sends it keyed by
 * track id. Synchronous: the send blocks on the broker acknowledgement so a failure propagates and
 * the correlation transaction rolls back, leaving the detection to be reprocessed rather than a
 * track persisted with no event announced.
 */
@Component
public class TrackEventKafkaPublisher implements TrackEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(TrackEventKafkaPublisher.class);
    private static final String SCHEMA_VERSION = "1.0.0";
    private static final long SEND_TIMEOUT_SECONDS = 5;

    private final KafkaTemplate<String, byte[]> kafkaTemplate;
    private final KalmanFilter kalman;

    public TrackEventKafkaPublisher(KafkaTemplate<String, byte[]> kafkaTemplate, KalmanFilter kalman) {
        this.kafkaTemplate = kafkaTemplate;
        this.kalman = kalman;
    }

    @Override
    public void publishCreated(Track track, TrackOutlook outlook) {
        send(TrackTopics.TRACK_CREATED, track, outlook);
        log.debug("Published track.created for {}", track.id());
    }

    @Override
    public void publishUpdated(Track track, TrackOutlook outlook) {
        send(TrackTopics.TRACK_UPDATED, track, outlook);
        log.debug("Published track.updated for {}", track.id());
    }

    @Override
    public void publishLost(Track track) {
        send(TrackTopics.TRACK_LOST, track, TrackOutlook.none());
        log.debug("Published track.lost for {}", track.id());
    }

    private void send(String topic, Track track, TrackOutlook outlook) {
        byte[] payload = toProto(track, outlook).toByteArray();
        try {
            kafkaTemplate.send(topic, track.id().toString(), payload).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted publishing to " + topic, ex);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not publish track to " + topic, ex);
        }
    }

    private pk.nust.cuas.track.v1.Track toProto(Track track, TrackOutlook outlook) {
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

    private GeoPosition.Builder toProtoPosition(GeoPoint point) {
        return GeoPosition.newBuilder()
                .setLatitude(point.latitude())
                .setLongitude(point.longitude())
                .setAltitudeM(point.altitudeM());
    }

    private pk.nust.cuas.track.v1.TrackStatus toProtoStatus(TrackStatus status) {
        return switch (status) {
            case TENTATIVE -> pk.nust.cuas.track.v1.TrackStatus.TRACK_STATUS_TENTATIVE;
            case CONFIRMED -> pk.nust.cuas.track.v1.TrackStatus.TRACK_STATUS_CONFIRMED;
            case COASTING -> pk.nust.cuas.track.v1.TrackStatus.TRACK_STATUS_COASTING;
            case LOST -> pk.nust.cuas.track.v1.TrackStatus.TRACK_STATUS_LOST;
        };
    }

    private Timestamp toTimestamp(Instant instant) {
        return Timestamp.newBuilder()
                .setSeconds(instant.getEpochSecond()).setNanos(instant.getNano()).build();
    }
}
