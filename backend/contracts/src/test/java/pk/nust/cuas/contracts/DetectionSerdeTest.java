package pk.nust.cuas.contracts;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Timestamp;
import org.junit.jupiter.api.Test;
import pk.nust.cuas.detection.v1.AcousticMeasurement;
import pk.nust.cuas.detection.v1.BoundingBox;
import pk.nust.cuas.detection.v1.DetectionRecord;
import pk.nust.cuas.detection.v1.GeoPosition;
import pk.nust.cuas.detection.v1.GnssMeasurement;
import pk.nust.cuas.detection.v1.Kinematics;
import pk.nust.cuas.detection.v1.Measurements;
import pk.nust.cuas.detection.v1.OpticalMeasurement;
import pk.nust.cuas.detection.v1.RadarMeasurement;
import pk.nust.cuas.detection.v1.RfMeasurement;
import pk.nust.cuas.detection.v1.SensorModality;

/**
 * Serialization round-trip coverage for every message in {@code detection.proto}
 * (ARCHITECTURE.md section 5.3). Each message is fully populated, serialised to the wire form,
 * parsed back, and asserted equal — proving the generated bindings encode and decode every field,
 * including the {@code Measurements} oneof for all five modalities.
 */
class DetectionSerdeTest {

    private static DetectionRecord.Builder baseRecord() {
        return DetectionRecord.newBuilder()
                .setDetectionId("01J8X4K2M9P3Q7R5T1V6W8Y0Z2")
                .setSensorId("RF-01")
                .setModality(SensorModality.MODALITY_RF)
                .setSensorTime(Timestamp.newBuilder().setSeconds(1_784_562_300).setNanos(500).build())
                .setIngestTime(Timestamp.newBuilder().setSeconds(1_784_562_301).setNanos(750).build())
                .setClockDriftMs(-3)
                .setSensorSeq(42L)
                .setPosition(GeoPosition.newBuilder()
                        .setLatitude(33.6844).setLongitude(73.0479)
                        .setAltitudeM(120.5).setHorizontalAccuracyM(2.5).setVerticalAccuracyM(4.0)
                        .setIsEstimated(true).build())
                .setKinematics(Kinematics.newBuilder()
                        .setRangeM(1200.0).setAzimuthDeg(48.0).setElevationDeg(5.5)
                        .setRadialVelocityMs(14.2).setSpeedMs(15.0).setHeadingDeg(228.0).build())
                .setQuality(0.87f)
                .putMetadata("site", "north")
                .setSchemaVersion("1.0.0")
                .setCorrelationId("corr-01");
    }

    private static DetectionRecord roundTrip(DetectionRecord original) throws InvalidProtocolBufferException {
        return DetectionRecord.parseFrom(original.toByteArray());
    }

    @Test
    void rfMeasurementRoundTrips() throws Exception {
        DetectionRecord original = baseRecord()
                .setMeasurements(Measurements.newBuilder()
                        .setRf(RfMeasurement.newBuilder()
                                .setCenterFrequencyHz(2.44e9).setBandwidthHz(20e6)
                                .setSignalStrengthDbm(-58.0).setSnrDb(18.0)
                                .setProtocolHint("OcuSync")
                                .setPsdFeatures(ByteString.copyFrom(new byte[]{1, 2, 3, 4}))
                                .setIqReference("s3://iq/abc").build()))
                .build();

        DetectionRecord parsed = roundTrip(original);

        assertThat(parsed).isEqualTo(original);
        assertThat(parsed.getMeasurements().hasRf()).isTrue();
        assertThat(parsed.getMeasurements().getRf().getProtocolHint()).isEqualTo("OcuSync");
        assertThat(parsed.getModality()).isEqualTo(SensorModality.MODALITY_RF);
        assertThat(parsed.getClockDriftMs()).isEqualTo(-3);
    }

    @Test
    void radarMeasurementRoundTrips() throws Exception {
        DetectionRecord original = baseRecord()
                .setModality(SensorModality.MODALITY_RADAR)
                .setMeasurements(Measurements.newBuilder()
                        .setRadar(RadarMeasurement.newBuilder()
                                .setRcsM2(0.021).setDopplerHz(480.0)
                                .setRangeDopplerMapRef("s3://rd/xyz").setRadarType("FMCW")
                                .setTrackQuality(7).build()))
                .build();

        DetectionRecord parsed = roundTrip(original);

        assertThat(parsed).isEqualTo(original);
        assertThat(parsed.getMeasurements().getPayloadCase())
                .isEqualTo(Measurements.PayloadCase.RADAR);
        assertThat(parsed.getMeasurements().getRadar().getRadarType()).isEqualTo("FMCW");
    }

    @Test
    void opticalMeasurementWithBoundingBoxRoundTrips() throws Exception {
        DetectionRecord original = baseRecord()
                .setModality(SensorModality.MODALITY_EO_IR)
                .setMeasurements(Measurements.newBuilder()
                        .setOptical(OpticalMeasurement.newBuilder()
                                .setBbox(BoundingBox.newBuilder()
                                        .setX(0.1f).setY(0.2f).setWidth(0.3f).setHeight(0.4f)
                                        .setConfidence(0.83f).build())
                                .setFrameRef("s3://frame/1").setSpectrum("VISIBLE")
                                .setIlluminanceLux(1200.0).setConsecutiveFrames(6).build()))
                .build();

        DetectionRecord parsed = roundTrip(original);

        assertThat(parsed).isEqualTo(original);
        assertThat(parsed.getMeasurements().getOptical().getBbox().getConfidence()).isEqualTo(0.83f);
    }

    @Test
    void acousticMeasurementRoundTrips() throws Exception {
        DetectionRecord original = baseRecord()
                .setModality(SensorModality.MODALITY_ACOUSTIC)
                .setMeasurements(Measurements.newBuilder()
                        .setAcoustic(AcousticMeasurement.newBuilder()
                                .setBearingDeg(134.0).setSplDb(62.0)
                                .setSpectrogramRef("s3://spec/1").setDominantFrequencyHz(220.0).build()))
                .build();

        DetectionRecord parsed = roundTrip(original);

        assertThat(parsed).isEqualTo(original);
        assertThat(parsed.getMeasurements().getAcoustic().getBearingDeg()).isEqualTo(134.0);
    }

    @Test
    void gnssMeasurementRoundTrips() throws Exception {
        DetectionRecord original = baseRecord()
                .setModality(SensorModality.MODALITY_GNSS)
                .setMeasurements(Measurements.newBuilder()
                        .setGnss(GnssMeasurement.newBuilder()
                                .setSatellitesVisible(9).setHdop(1.2)
                                .setSpoofingSuspected(true).setSignalAnomalyScore(0.44).build()))
                .build();

        DetectionRecord parsed = roundTrip(original);

        assertThat(parsed).isEqualTo(original);
        assertThat(parsed.getMeasurements().getGnss().getSpoofingSuspected()).isTrue();
    }

    @Test
    void emptyRecordRoundTrips() throws Exception {
        DetectionRecord original = DetectionRecord.getDefaultInstance();

        DetectionRecord parsed = roundTrip(original);

        assertThat(parsed).isEqualTo(original);
        assertThat(parsed.getMeasurements().getPayloadCase())
                .isEqualTo(Measurements.PayloadCase.PAYLOAD_NOT_SET);
    }

    @Test
    void allModalityEnumValuesPreserved() throws Exception {
        for (SensorModality modality : SensorModality.values()) {
            if (modality == SensorModality.UNRECOGNIZED) {
                continue;
            }
            DetectionRecord original = DetectionRecord.newBuilder().setModality(modality).build();
            assertThat(roundTrip(original).getModality()).isEqualTo(modality);
        }
    }
}
