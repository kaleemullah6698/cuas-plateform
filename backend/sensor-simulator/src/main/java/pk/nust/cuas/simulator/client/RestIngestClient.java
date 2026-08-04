package pk.nust.cuas.simulator.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import java.util.List;
import java.util.StringJoiner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import pk.nust.cuas.detection.v1.DetectionRecord;
import pk.nust.cuas.simulator.config.Transport;

/**
 * Drives the real REST intake, {@code POST /ingest/detections} (ARCHITECTURE.md section 4.1.4).
 *
 * <p>Renders each detection to the canonical protobuf JSON — the exact wire form the endpoint
 * accepts — and posts it in batches, summing the accepted and rejected counts the endpoint reports.
 * Batching bounds the size of any single request, mirroring how a real adapter would flush.
 */
public class RestIngestClient implements IngestClient {

    private static final Logger log = LoggerFactory.getLogger(RestIngestClient.class);

    private final RestClient restClient;
    private final int batchSize;

    public RestIngestClient(RestClient restClient, int batchSize) {
        this.restClient = restClient;
        this.batchSize = batchSize;
    }

    @Override
    public Transport transport() {
        return Transport.REST;
    }

    @Override
    public IngestReport send(List<DetectionRecord> detections) {
        IngestReport report = IngestReport.empty(Transport.REST);
        for (int start = 0; start < detections.size(); start += batchSize) {
            List<DetectionRecord> batch = detections.subList(start, Math.min(start + batchSize, detections.size()));
            BatchResult result = post(batch);
            report = report.plus(batch.size(), result.accepted(), result.rejected());
        }
        log.info("REST intake: sent {}, accepted {}, rejected {}",
                report.sent(), report.accepted(), report.rejected());
        return report;
    }

    private BatchResult post(List<DetectionRecord> batch) {
        BatchResult result = restClient.post()
                .uri("/ingest/detections")
                .contentType(MediaType.APPLICATION_JSON)
                .body(toBatchJson(batch))
                .retrieve()
                .body(BatchResult.class);
        return result == null ? new BatchResult(0, batch.size()) : result;
    }

    private String toBatchJson(List<DetectionRecord> batch) {
        StringJoiner detections = new StringJoiner(",", "{\"detections\":[", "]}");
        for (DetectionRecord record : batch) {
            detections.add(toJson(record));
        }
        return detections.toString();
    }

    private String toJson(DetectionRecord record) {
        try {
            return JsonFormat.printer().omittingInsignificantWhitespace().print(record);
        } catch (InvalidProtocolBufferException ex) {
            throw new IllegalStateException("could not render a generated detection to JSON", ex);
        }
    }

    /** The fields of the endpoint's response the simulator needs; the rest are ignored. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record BatchResult(int accepted, int rejected) {
    }
}
