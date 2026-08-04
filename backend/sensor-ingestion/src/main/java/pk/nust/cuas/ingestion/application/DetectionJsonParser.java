package pk.nust.cuas.ingestion.application;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import pk.nust.cuas.detection.v1.DetectionRecord;

/**
 * Parses one canonical {@code DetectionRecord} from its protobuf-JSON representation (ARCHITECTURE.md
 * section 5.3), shared by the batch intake and the dataset replay so the two agree on exactly what
 * valid JSON is.
 *
 * <p>A parse failure returns empty rather than throwing: both callers treat a malformed record as a
 * rejection to be counted, not an error that fails the whole request. The parser message can echo
 * the malformed input, so it is logged, never returned to the caller.
 */
@Component
public class DetectionJsonParser {

    private static final Logger log = LoggerFactory.getLogger(DetectionJsonParser.class);

    /**
     * @param json one detection as protobuf JSON
     * @return the parsed record, or empty when the JSON does not conform to the schema
     */
    public Optional<DetectionRecord> parse(String json) {
        DetectionRecord.Builder builder = DetectionRecord.newBuilder();
        try {
            JsonFormat.parser().ignoringUnknownFields().merge(json, builder);
            return Optional.of(builder.build());
        } catch (InvalidProtocolBufferException ex) {
            log.warn("Detection is not valid DetectionRecord JSON: {}", ex.getMessage());
            return Optional.empty();
        }
    }
}
