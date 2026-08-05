package pk.nust.cuas.orchestrator.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * A model-version change for {@code POST /ai/services/{id}/model-version} (ARCHITECTURE.md sections
 * 4.1.6, 6.6).
 *
 * <p>§6.6's rollback lever: "Configuration change: {@code model_version: "1.2.0"}. No redeploy, no
 * code change." The canary fields let a version be introduced to a share of traffic first.
 *
 * @param modelVersion      the baseline version every non-canary track is asked of
 * @param canaryVersion     an optional version for a share of tracks; blank clears the canary
 * @param canaryPercentage  the share of tracks on the canary, 0-100
 */
@Schema(description = "A model version change, optionally with a canary share")
public record ModelVersionRequest(

        @Schema(description = "Baseline semantic version", example = "1.3.0")
        @NotBlank(message = "modelVersion is required")
        @Pattern(regexp = "\\d+\\.\\d+\\.\\d+(-[0-9A-Za-z.-]+)?",
                message = "modelVersion must be semantic, e.g. 1.3.0")
        String modelVersion,

        @Schema(description = "Canary version, or blank for none", example = "1.4.0")
        @Pattern(regexp = "|\\d+\\.\\d+\\.\\d+(-[0-9A-Za-z.-]+)?",
                message = "canaryVersion must be blank or semantic, e.g. 1.4.0")
        String canaryVersion,

        @Schema(description = "Share of tracks routed to the canary", example = "10")
        @Min(value = 0, message = "canaryPercentage must be 0-100")
        @Max(value = 100, message = "canaryPercentage must be 0-100")
        int canaryPercentage) {
}
