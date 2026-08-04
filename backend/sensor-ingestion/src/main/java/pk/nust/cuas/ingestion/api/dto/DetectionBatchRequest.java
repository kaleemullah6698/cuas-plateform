package pk.nust.cuas.ingestion.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * The request body of {@code POST /ingest/detections}: a batch of canonical {@code DetectionRecord}
 * objects in protobuf JSON form (ARCHITECTURE.md sections 4.1.4, 5.3).
 *
 * <p>Each element is carried as a raw JSON node rather than a hand-written mirror of the
 * {@code DetectionRecord} tree. The canonical schema is the one wire contract; a parallel DTO
 * hierarchy for the same eight nested messages would be a second, drifting source of truth for it
 * (CLAUDE.md section 0). The nodes are parsed against the contract with protobuf's own JSON parser
 * at the controller, and the header of each is validated by the domain validator; a per-record
 * parse or validation failure is reported in the response without failing the whole batch.
 *
 * @param detections the detections to ingest; at least one
 */
public record DetectionBatchRequest(
        @NotEmpty(message = "at least one detection is required") List<JsonNode> detections) {
}
