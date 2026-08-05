package pk.nust.cuas.orchestrator.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * One registered AI service and its current health, for {@code GET /ai/services} (ARCHITECTURE.md
 * section 4.1.6).
 *
 * <p>A distinct type from the domain descriptor (CLAUDE.md §2); the domain never crosses the API
 * boundary.
 *
 * @param id           the configured service id
 * @param modalities   the detection modalities it accepts
 * @param mode         {@code MOCK} or {@code GRPC}
 * @param endpoint     where it is reached, blank for the mock
 * @param timeoutMs    the orchestrator-enforced budget
 * @param modelVersion the pinned baseline version, blank for the service default
 * @param canaryVersion    the canary version, blank when none
 * @param canaryPercentage the share of tracks on the canary
 * @param maxConcurrent    bulkhead permits
 * @param healthy      whether it is currently serving — a fact reported, never an error
 */
@Schema(description = "A registered AI service and its current health")
public record AiServiceResponse(
        String id,
        List<String> modalities,
        String mode,
        String endpoint,
        long timeoutMs,
        String modelVersion,
        String canaryVersion,
        int canaryPercentage,
        int maxConcurrent,
        boolean healthy) {
}
