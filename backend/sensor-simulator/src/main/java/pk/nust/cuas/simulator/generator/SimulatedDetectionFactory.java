package pk.nust.cuas.simulator.generator;

import com.google.protobuf.Timestamp;
import java.time.Instant;
import org.springframework.stereotype.Component;
import pk.nust.cuas.detection.v1.BoundingBox;
import pk.nust.cuas.detection.v1.DetectionRecord;
import pk.nust.cuas.detection.v1.GeoPosition;
import pk.nust.cuas.detection.v1.Kinematics;
import pk.nust.cuas.detection.v1.Measurements;
import pk.nust.cuas.detection.v1.OpticalMeasurement;
import pk.nust.cuas.detection.v1.RadarMeasurement;
import pk.nust.cuas.detection.v1.RfMeasurement;
import pk.nust.cuas.detection.v1.SensorModality;

/**
 * Builds synthetic but well-formed {@link DetectionRecord}s (ARCHITECTURE.md section 5.3).
 *
 * <p>Every record carries a valid header — sensor id, modality, sensor time, sequence, and a quality
 * in [0,1] — so it passes the ingestion validator, plus a measurement matching its modality so the
 * record is realistic rather than a bare header. Values vary deterministically with the sequence
 * number so a run is reproducible: no randomness, so the same configuration produces the same
 * detections and a test can assert on them.
 */
@Component
public class SimulatedDetectionFactory {

    private static final double BASE_LAT = 33.6844;   // Islamabad, a plausible protected sector
    private static final double BASE_LON = 73.0479;

    /**
     * @param sensorId   the emitting sensor
     * @param modality   the sensor modality; the measurement is chosen to match
     * @param sensorSeq  the per-sensor sequence number
     * @param sensorTime the sensor-reported time
     * @return a valid, modality-appropriate detection
     */
    public DetectionRecord create(String sensorId, SensorModality modality, long sensorSeq, Instant sensorTime) {
        double wobble = (sensorSeq % 50) / 1000.0d;
        DetectionRecord.Builder record = DetectionRecord.newBuilder()
                .setSensorId(sensorId)
                .setModality(modality)
                .setSensorTime(toTimestamp(sensorTime))
                .setSensorSeq(sensorSeq)
                .setQuality(0.5f + (sensorSeq % 50) / 100.0f) // 0.50..0.99, always within [0,1]
                .setPosition(GeoPosition.newBuilder()
                        .setLatitude(BASE_LAT + wobble)
                        .setLongitude(BASE_LON + wobble)
                        .setAltitudeM(80.0d + (sensorSeq % 100))
                        .setHorizontalAccuracyM(5.0d))
                .setKinematics(Kinematics.newBuilder()
                        .setRangeM(1200.0d - (sensorSeq % 100))
                        .setSpeedMs(12.0d)
                        .setHeadingDeg((sensorSeq * 7) % 360));
        record.setMeasurements(measurementFor(modality, sensorSeq));
        return record.build();
    }

    private Measurements measurementFor(SensorModality modality, long sensorSeq) {
        Measurements.Builder measurements = Measurements.newBuilder();
        return switch (modality) {
            case MODALITY_RADAR -> measurements.setRadar(RadarMeasurement.newBuilder()
                    .setRcsM2(0.05d)
                    .setDopplerHz(400.0d + (sensorSeq % 50))
                    .setRadarType("FMCW")
                    .setTrackQuality((int) (sensorSeq % 5) + 1)).build();
            case MODALITY_EO_IR -> measurements.setOptical(OpticalMeasurement.newBuilder()
                    .setBbox(BoundingBox.newBuilder()
                            .setX(0.4f).setY(0.4f).setWidth(0.1f).setHeight(0.1f).setConfidence(0.8f))
                    .setSpectrum("IR")
                    .setConsecutiveFrames((int) (sensorSeq % 30))).build();
            default -> measurements.setRf(RfMeasurement.newBuilder()
                    .setCenterFrequencyHz(2_450_000_000d)
                    .setBandwidthHz(20_000_000d)
                    .setSignalStrengthDbm(-60.0d + (sensorSeq % 20))
                    .setSnrDb(20.0d)
                    .setProtocolHint("OcuSync")).build();
        };
    }

    private Timestamp toTimestamp(Instant instant) {
        return Timestamp.newBuilder()
                .setSeconds(instant.getEpochSecond()).setNanos(instant.getNano()).build();
    }
}
