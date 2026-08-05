package pk.nust.cuas.orchestrator.api.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pk.nust.cuas.orchestrator.api.dto.AiServiceResponse;
import pk.nust.cuas.orchestrator.api.dto.ModelVersionRequest;
import pk.nust.cuas.orchestrator.application.AiServiceAdministration;
import pk.nust.cuas.orchestrator.domain.model.ServiceId;

/**
 * The AI service registry and its admin surface (ARCHITECTURE.md section 4.1.6).
 *
 * <p>{@code GET /ai/services} reports the registry and each service's live health;
 * {@code POST /ai/services/{id}/model-version} is §6.6's rollback and canary lever — "configuration
 * change, no redeploy, no code change".
 *
 * <p>Both require {@code ai:configure}, which §7.4 grants to ENGINEER and ADMIN only. Changing which
 * model scores a threat is a change to what the system concludes about the airspace, so it sits with
 * the roles that own the platform's configuration rather than with the operators who act on its
 * output.
 *
 * <p>The read is guarded too, not just the write: the registry discloses every AI service's endpoint
 * and health, which is a map of the deployment's internal topology.
 */
@RestController
@RequestMapping("/ai/services")
@Tag(name = "AI services", description = "The registry of AI services and their model versions")
public class AiServiceController {

    private static final Logger log = LoggerFactory.getLogger(AiServiceController.class);

    private final AiServiceAdministration administration;

    public AiServiceController(AiServiceAdministration administration) {
        this.administration = administration;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ai:configure')")
    @Operation(summary = "List registered AI services with their live health",
            description = "The registry as configured, plus each service's current serving status. "
                    + "An unreachable service is reported as unhealthy rather than failing the call.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The registry"),
            @ApiResponse(responseCode = "401", description = "Unauthenticated"),
            @ApiResponse(responseCode = "403", description = "Missing ai:configure")})
    public List<AiServiceResponse> list() {
        log.debug("Listing AI services");
        return administration.list();
    }

    @PostMapping("/{id}/model-version")
    @PreAuthorize("hasAuthority('ai:configure')")
    @Operation(summary = "Change a service's model version, optionally with a canary share",
            description = "Section 6.6's rollback lever. Takes effect immediately for subsequent "
                    + "requests; no redeploy. A canary share routes that percentage of *tracks* — not "
                    + "calls — to the canary version, so a track's classifications stay comparable.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The updated service"),
            @ApiResponse(responseCode = "400", description = "Invalid version or canary settings"),
            @ApiResponse(responseCode = "401", description = "Unauthenticated"),
            @ApiResponse(responseCode = "403", description = "Missing ai:configure"),
            @ApiResponse(responseCode = "404", description = "No service is registered under that id")})
    public ResponseEntity<AiServiceResponse> setModelVersion(
            @PathVariable("id") String id,
            @Valid @RequestBody ModelVersionRequest request) {

        log.info("Setting model version of {} to {} (canary {} at {}%)", id, request.modelVersion(),
                request.canaryVersion().isBlank() ? "none" : request.canaryVersion(),
                request.canaryPercentage());

        return administration.setModelVersion(new ServiceId(id),
                        Optional.of(request.modelVersion()),
                        blankToEmpty(request.canaryVersion()),
                        request.canaryPercentage())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private static Optional<String> blankToEmpty(String value) {
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value.trim());
    }
}
