package pk.nust.cuas.ingestion.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.util.JsonFormat;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import pk.nust.cuas.detection.v1.DetectionRecord;
import pk.nust.cuas.ingestion.support.Detections;

class DetectionJsonParserTest {

    private final DetectionJsonParser parser = new DetectionJsonParser();

    @Test
    void parsesCanonicalDetectionJson() throws Exception {
        DetectionRecord original = Detections.rf("rf-01", 7L, Instant.parse("2026-08-04T12:00:00Z"));
        String json = JsonFormat.printer().print(original);

        assertThat(parser.parse(json)).hasValueSatisfying(parsed -> {
            assertThat(parsed.getSensorId()).isEqualTo("rf-01");
            assertThat(parsed.getSensorSeq()).isEqualTo(7L);
        });
    }

    @Test
    void returnsEmptyForMalformedJson() {
        assertThat(parser.parse("{ not valid json")).isEmpty();
    }

    @Test
    void returnsEmptyForJsonWithAFieldOfTheWrongType() {
        // sensorSeq is a uint64; a non-numeric string is not coercible.
        assertThat(parser.parse("{\"sensorId\":\"rf-01\",\"sensorSeq\":\"not-a-number\"}")).isEmpty();
    }
}
