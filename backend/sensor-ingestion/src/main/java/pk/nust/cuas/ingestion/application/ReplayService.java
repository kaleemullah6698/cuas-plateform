package pk.nust.cuas.ingestion.application;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import pk.nust.cuas.detection.v1.DetectionRecord;
import pk.nust.cuas.ingestion.api.dto.ReplaySummary;

/**
 * Replays a recorded dataset of detections through the real ingestion pipeline (ARCHITECTURE.md
 * section 4.1.4, {@code POST /ingest/replay}).
 *
 * <p>The dataset is newline-delimited JSON: one canonical {@code DetectionRecord} per line. Each is
 * parsed and handed to the same {@link RawIntakeService} the live intake uses, so a replayed
 * detection is validated, published, consumed, normalised, and de-duplicated exactly as a live one
 * — there is no shortcut into Kafka or the database. That is the point of replay: to exercise the
 * whole pipeline with reproducible input.
 *
 * <p>The stream is consumed line by line rather than read whole, so a large dataset does not have to
 * fit in memory. Blank lines are skipped so a trailing newline is harmless.
 */
@Service
public class ReplayService {

    private static final Logger log = LoggerFactory.getLogger(ReplayService.class);

    private final DetectionJsonParser parser;
    private final RawIntakeService intake;

    public ReplayService(DetectionJsonParser parser, RawIntakeService intake) {
        this.parser = parser;
        this.intake = intake;
    }

    /**
     * Reads the dataset and feeds each detection through the intake.
     *
     * @param reader the newline-delimited JSON dataset
     * @return the counts of read, accepted, and rejected detections
     * @throws IOException if the dataset cannot be read
     */
    public ReplaySummary replay(BufferedReader reader) throws IOException {
        int total = 0;
        int accepted = 0;
        int rejected = 0;
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isBlank()) {
                continue;
            }
            total++;
            Optional<DetectionRecord> record = parser.parse(line);
            if (record.isPresent() && intake.accept(record.get()).accepted()) {
                accepted++;
            } else {
                rejected++;
            }
        }
        log.info("Replay complete: {} read, {} accepted, {} rejected", total, accepted, rejected);
        return new ReplaySummary(total, accepted, rejected);
    }
}
