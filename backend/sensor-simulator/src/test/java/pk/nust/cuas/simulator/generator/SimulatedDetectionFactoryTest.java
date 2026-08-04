package pk.nust.cuas.simulator.generator;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import pk.nust.cuas.detection.v1.DetectionRecord;
import pk.nust.cuas.detection.v1.SensorModality;

class SimulatedDetectionFactoryTest {

    private static final Instant TIME = Instant.parse("2026-08-04T12:00:00Z");
    private final SimulatedDetectionFactory factory = new SimulatedDetectionFactory();

    @Test
    void producesAValidHeaderThatWouldPassIngestionValidation() {
        DetectionRecord record = factory.create("sim-rf-0", SensorModality.MODALITY_RF, 5L, TIME);

        assertThat(record.getSensorId()).isEqualTo("sim-rf-0");
        assertThat(record.getModality()).isEqualTo(SensorModality.MODALITY_RF);
        assertThat(record.hasSensorTime()).isTrue();
        assertThat(record.getSensorSeq()).isEqualTo(5L);
        assertThat(record.getQuality()).isBetween(0.0f, 1.0f);
    }

    @Test
    void qualityStaysWithinTheUnitIntervalAcrossManySequences() {
        for (long seq = 0; seq < 200; seq++) {
            float quality = factory.create("s", SensorModality.MODALITY_RF, seq, TIME).getQuality();
            assertThat(quality).as("seq %d", seq).isBetween(0.0f, 1.0f);
        }
    }

    @Test
    void theMeasurementMatchesTheModality() {
        assertThat(factory.create("s", SensorModality.MODALITY_RF, 1L, TIME).getMeasurements().hasRf()).isTrue();
        assertThat(factory.create("s", SensorModality.MODALITY_RADAR, 1L, TIME).getMeasurements().hasRadar()).isTrue();
        assertThat(factory.create("s", SensorModality.MODALITY_EO_IR, 1L, TIME).getMeasurements().hasOptical()).isTrue();
    }

    @Test
    void generationIsDeterministicForReproducibleRuns() {
        DetectionRecord first = factory.create("s", SensorModality.MODALITY_RADAR, 9L, TIME);
        DetectionRecord second = factory.create("s", SensorModality.MODALITY_RADAR, 9L, TIME);

        assertThat(first).isEqualTo(second);
    }
}
