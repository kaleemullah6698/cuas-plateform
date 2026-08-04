package pk.nust.cuas.ingestion.api.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pk.nust.cuas.detection.v1.DetectionRecord;
import pk.nust.cuas.ingestion.api.dto.BatchIngestResponse;
import pk.nust.cuas.ingestion.api.dto.DetectionBatchRequest;
import pk.nust.cuas.ingestion.api.dto.RejectedDetection;
import pk.nust.cuas.ingestion.application.IntakeResult;
import pk.nust.cuas.ingestion.application.RawIntakeService;
import pk.nust.cuas.ingestion.config.IngestionProperties;
import pk.nust.cuas.ingestion.domain.exception.BatchTooLargeException;

/**
 * Batch REST intake for sensor detections (ARCHITECTURE.md section 4.1.4,
 * {@code POST /ingest/detections}).
 *
 * <p>The endpoint is machine-facing — a sensor adapter, not an operator — so it carries no RBAC
 * annotation: sensors are not users, and sensor authentication is an infrastructure concern (mTLS
 * or a trusted network), as decided for M4. Each detection is parsed against the canonical schema,
 * its header validated, and, if valid, published to the raw topic; a per-detection failure is
 * reported in the response body without failing the rest of the batch, so one malformed record from
 * one sensor never rejects a co-batched detection from another.
 */
@RestController
@RequestMapping("/ingest")
@Tag(name = "Detection intake", description = "Accepts sensor detections into the ingestion pipeline")
public class DetectionIntakeController {

    private static final Logger log = LoggerFactory.getLogger(DetectionIntakeController.class);

    private final RawIntakeService intake;
    private final int maxBatchSize;

    public DetectionIntakeController(RawIntakeService intake, IngestionProperties properties) {
        this.intake = intake;
        this.maxBatchSize = properties.batch().maxSize();
    }

    @PostMapping("/detections")
    @Operation(summary = "Ingest a batch of detections",
            description = "Accepts canonical DetectionRecord objects as protobuf JSON, validates each"
                    + " header, and publishes the valid ones to sensor.detection.raw.v1.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Batch processed; body reports accepted and rejected counts"),
            @ApiResponse(responseCode = "400", description = "Empty batch"),
            @ApiResponse(responseCode = "413", description = "Batch exceeds the configured maximum size"),
            @ApiResponse(responseCode = "503", description = "The broker is unavailable; retry the batch")
    })
    public ResponseEntity<BatchIngestResponse> ingest(@Valid @RequestBody DetectionBatchRequest request) {
        List<JsonNode> detections = request.detections();
        if (detections.size() > maxBatchSize) {
            throw new BatchTooLargeException(detections.size(), maxBatchSize);
        }

        List<RejectedDetection> rejections = new ArrayList<>();
        int accepted = 0;
        for (int index = 0; index < detections.size(); index++) {
            DetectionRecord record = parse(detections.get(index), index, rejections);
            if (record == null) {
                continue;
            }
            IntakeResult result = intake.accept(record);
            if (result.accepted()) {
                accepted++;
            } else {
                rejections.add(new RejectedDetection(index, result.sensorSeq(), result.violations()));
            }
        }

        log.debug("Ingest batch: {} accepted, {} rejected", accepted, rejections.size());
        return ResponseEntity.accepted()
                .body(new BatchIngestResponse(accepted, rejections.size(), rejections));
    }

    private DetectionRecord parse(JsonNode node, int index, List<RejectedDetection> rejections) {
        DetectionRecord.Builder builder = DetectionRecord.newBuilder();
        try {
            JsonFormat.parser().ignoringUnknownFields().merge(node.toString(), builder);
            return builder.build();
        } catch (InvalidProtocolBufferException ex) {
            // The parser's message can echo the malformed input, so it is logged, not returned.
            log.warn("Detection at index {} is not valid DetectionRecord JSON: {}", index, ex.getMessage());
            rejections.add(new RejectedDetection(index, 0L, List.of("malformed detection JSON")));
            return null;
        }
    }
}
