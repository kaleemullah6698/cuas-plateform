package pk.nust.cuas.ingestion.api.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.io.BufferedReader;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pk.nust.cuas.ingestion.api.dto.ReplaySummary;
import pk.nust.cuas.ingestion.application.ReplayService;

/**
 * Development-only dataset replay (ARCHITECTURE.md section 4.1.4, {@code POST /ingest/replay}).
 *
 * <p>Registered only when {@code ingestion.replay.enabled} is true. It is off by default, so in any
 * deployment facing real sensors the bean does not exist and the path returns 404 — replay can push
 * arbitrary detections into the pipeline, which must never be reachable in production. The feature
 * flag gates the endpoint's existence rather than its behaviour, so a disabled instance exposes no
 * replay surface at all.
 *
 * <p>The body is newline-delimited JSON, one {@code DetectionRecord} per line, streamed straight to
 * {@link ReplayService}; the servlet reader honours the request charset and the dataset is never
 * held whole in memory.
 */
@RestController
@RequestMapping("/ingest")
@ConditionalOnProperty(name = "ingestion.replay.enabled", havingValue = "true")
@Tag(name = "Dataset replay", description = "Development-only replay of a recorded detection dataset")
public class ReplayController {

    private static final Logger log = LoggerFactory.getLogger(ReplayController.class);

    private final ReplayService replayService;

    public ReplayController(ReplayService replayService) {
        this.replayService = replayService;
    }

    @PostMapping(value = "/replay", consumes = {"application/x-ndjson", "text/plain"})
    @Operation(summary = "Replay a recorded detection dataset (development only)",
            description = "Reads newline-delimited DetectionRecord JSON and feeds each detection"
                    + " through the real ingestion pipeline. Registered only when replay is enabled.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Replay complete; body reports read, accepted, and rejected counts"),
            @ApiResponse(responseCode = "503", description = "The broker became unavailable mid-replay")
    })
    public ReplaySummary replay(HttpServletRequest request) throws IOException {
        log.info("Starting dataset replay");
        try (BufferedReader reader = request.getReader()) {
            return replayService.replay(reader);
        }
    }
}
