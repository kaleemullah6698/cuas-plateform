package pk.nust.cuas.ingestion.support;

import com.google.protobuf.Timestamp;
import java.time.Instant;
import pk.nust.cuas.detection.v1.DetectionRecord;
import pk.nust.cuas.detection.v1.RfMeasurement;
import pk.nust.cuas.detection.v1.SensorModality;

/**
 * Builders for canonical {@link DetectionRecord}s used across the tests, so each test states only
 * the fields it cares about.
 */
public final class Detections {

    private Detections() {
    }

    /** A valid RF detection from {@code sensorId} at {@code sensorTime} with the given sequence. */
    public static DetectionRecord rf(String sensorId, long sensorSeq, Instant sensorTime) {
        return DetectionRecord.newBuilder()
                .setSensorId(sensorId)
                .setModality(SensorModality.MODALITY_RF)
                .setSensorTime(timestamp(sensorTime))
                .setSensorSeq(sensorSeq)
                .setQuality(0.9f)
                .setMeasurements(DetectionRecord.newBuilder().getMeasurements().toBuilder()
                        .setRf(RfMeasurement.newBuilder()
                                .setCenterFrequencyHz(2_450_000_000d)
                                .setProtocolHint("OcuSync")))
                .build();
    }

    public static Timestamp timestamp(Instant instant) {
        return Timestamp.newBuilder()
                .setSeconds(instant.getEpochSecond())
                .setNanos(instant.getNano())
                .build();
    }
}
