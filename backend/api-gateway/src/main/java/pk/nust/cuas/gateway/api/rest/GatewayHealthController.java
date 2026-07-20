package pk.nust.cuas.gateway.api.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.actuate.health.HealthComponent;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.Status;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import pk.nust.cuas.gateway.api.dto.HealthResponse;

/**
 * The liveness and readiness endpoints named in ARCHITECTURE.md section 4.1.1.
 *
 * <p>Section 4.1.1 specifies {@code GET /health} and {@code GET /health/ready} as part of the
 * gateway's API, which are not the paths Actuator serves by default. Rather than run two
 * independent notions of health, these delegate to the Actuator endpoint — so a Redis or Kafka
 * indicator contributed by a starter is reflected here automatically — and present it under the
 * documented paths and a stable, minimal shape.
 *
 * <p>Readiness is what an orchestrator removes an instance from rotation on, so it reports the
 * readiness group: the gateway is unready when a dependency it cannot serve without, such as the
 * Redis holding its rate-limit buckets, is unreachable.
 */
@RestController
@Tag(name = "Health", description = "Liveness and readiness probes")
public class GatewayHealthController {

    private final HealthEndpoint healthEndpoint;

    public GatewayHealthController(HealthEndpoint healthEndpoint) {
        this.healthEndpoint = healthEndpoint;
    }

    /**
     * @return {@code 200} while the process is alive and its components are up, {@code 503}
     *         otherwise
     */
    @GetMapping("/health")
    @Operation(summary = "Liveness",
            description = "Whether the gateway process is running and its components are healthy.")
    @ApiResponse(responseCode = "200", description = "The gateway is healthy")
    @ApiResponse(responseCode = "503", description = "At least one component is down")
    public ResponseEntity<HealthResponse> health() {
        return respond(healthEndpoint.health());
    }

    /**
     * @return {@code 200} when the gateway can serve traffic, {@code 503} while it cannot
     */
    @GetMapping("/health/ready")
    @Operation(summary = "Readiness",
            description = "Whether the gateway can serve traffic. An instance reporting 503 here "
                    + "should be taken out of rotation rather than restarted.")
    @ApiResponse(responseCode = "200", description = "The gateway is ready to serve traffic")
    @ApiResponse(responseCode = "503", description = "The gateway is not ready")
    public ResponseEntity<HealthResponse> ready() {
        HealthComponent readiness = healthEndpoint.healthForPath("readiness");
        return respond(readiness != null ? readiness : healthEndpoint.health());
    }

    private ResponseEntity<HealthResponse> respond(HealthComponent component) {
        Status status = component.getStatus();
        boolean up = Status.UP.equals(status);
        return ResponseEntity
                .status(up ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
                .body(new HealthResponse(status.getCode()));
    }
}
