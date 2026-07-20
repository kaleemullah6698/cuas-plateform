package pk.nust.cuas.gateway.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The probe response of ARCHITECTURE.md section 4.1.1.
 *
 * <p>Only the aggregate status. Actuator's own {@code /actuator/health} carries per-component
 * detail and is exposed on the management surface where it is not reachable from outside; a probe
 * answered on the public ingress must not enumerate which internal dependency is failing.
 *
 * @param status {@code UP}, {@code DOWN}, or {@code OUT_OF_SERVICE}
 */
@Schema(description = "Aggregate health status")
public record HealthResponse(
        @Schema(description = "Aggregate status", example = "UP") String status) {
}
