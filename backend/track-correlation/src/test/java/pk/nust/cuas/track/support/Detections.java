package pk.nust.cuas.track.support;

import com.google.protobuf.Timestamp;
import java.time.Instant;
import pk.nust.cuas.detection.v1.DetectionRecord;
import pk.nust.cuas.detection.v1.GeoPosition;
import pk.nust.cuas.detection.v1.Kinematics;
import pk.nust.cuas.detection.v1.SensorModality;

/** Builders for normalised {@link DetectionRecord}s used across the track-correlation tests. */
public final class Detections {

    private Detections() {
    }

    /** A normalised detection at a position, as track-correlation consumes it off the topic. */
    public static DetectionRecord normalized(String detectionId, String sensorId, long sensorSeq,
                                             double latitude, double longitude, Instant ingestTime) {
        return DetectionRecord.newBuilder()
                .setDetectionId(detectionId)
                .setSensorId(sensorId)
                .setSensorSeq(sensorSeq)
                .setModality(SensorModality.MODALITY_RF)
                .setSensorTime(timestamp(ingestTime.minusMillis(50)))
                .setIngestTime(timestamp(ingestTime))
                .setPosition(GeoPosition.newBuilder()
                        .setLatitude(latitude).setLongitude(longitude).setAltitudeM(100.0d))
                .setKinematics(Kinematics.newBuilder().setSpeedMs(12.0d).setHeadingDeg(90.0d))
                .setQuality(0.9f)
                .setSchemaVersion("1.0.0")
                .setCorrelationId("corr-" + detectionId)
                .build();
    }

    public static Timestamp timestamp(Instant instant) {
        return Timestamp.newBuilder()
                .setSeconds(instant.getEpochSecond()).setNanos(instant.getNano()).build();
    }
}
