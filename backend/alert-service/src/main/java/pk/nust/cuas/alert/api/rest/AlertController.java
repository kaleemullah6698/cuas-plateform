package pk.nust.cuas.alert.api.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pk.nust.cuas.alert.api.dto.AlertRequests;
import pk.nust.cuas.alert.api.dto.AlertResponse;
import pk.nust.cuas.alert.application.AlertLifecycleService;
import pk.nust.cuas.alert.application.AlertQueryService;
import pk.nust.cuas.alert.domain.model.AlertDisposition;
import pk.nust.cuas.alert.domain.model.AlertStatus;
import pk.nust.cuas.alert.domain.model.Severity;
import pk.nust.cuas.commons.correlation.CorrelationId;

/**
 * The alert API (ARCHITECTURE.md section 4.1.8).
 *
 * <p>Permissions come straight from section 7.4's matrix, which already anticipated this service:
 * {@code alert:read} for the queue, {@code alert:acknowledge} to stop an escalation timer,
 * {@code alert:resolve} to close one. No new permission was invented — the matrix is closed, and
 * adding to it is an architecture change.
 *
 * <p><strong>The acting user comes from the JWT, never from the body.</strong> An acknowledgement
 * stops an escalation and a resolution closes an alert; both are recorded against a person, and a
 * body-supplied actor would let one operator act in another's name. That is the whole value of the
 * record.
 *
 * <p>There is no endpoint that raises an alert. Alerts are raised only by consuming a threat
 * transition — an HTTP surface that could manufacture one would let a caller put anything on the
 * operator's screen.
 */
@RestController
@RequestMapping("/alerts")
@Validated
@Tag(name = "Alerts", description = "The operator alert queue and its lifecycle")
public class AlertController {

    private static final Logger log = LoggerFactory.getLogger(AlertController.class);

    private final AlertQueryService alerts;
    private final AlertLifecycleService lifecycle;

    public AlertController(AlertQueryService alerts, AlertLifecycleService lifecycle) {
        this.alerts = alerts;
        this.lifecycle = lifecycle;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('alert:read')")
    @Operation(summary = "List alerts",
            description = "Newest raised first, optionally filtered by status, severity and sector.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The matching alerts"),
            @ApiResponse(responseCode = "400", description = "An invalid filter or limit"),
            @ApiResponse(responseCode = "403", description = "Missing the alert:read permission")
    })
    public List<AlertResponse> list(
            @RequestParam Optional<AlertStatus> status,
            @RequestParam Optional<Severity> severity,
            @RequestParam Optional<String> sector,
            @RequestParam(defaultValue = "100") @Min(1) @Max(1000) int limit) {
        return alerts.list(status, severity, sector, limit).stream()
                .map(AlertResponse::from)
                .toList();
    }

    @GetMapping("/{alertId}")
    @PreAuthorize("hasAuthority('alert:read')")
    @Operation(summary = "Get an alert by id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The alert"),
            @ApiResponse(responseCode = "400", description = "The id is not a UUID"),
            @ApiResponse(responseCode = "403", description = "Missing the alert:read permission"),
            @ApiResponse(responseCode = "404", description = "No such alert")
    })
    public AlertResponse get(@PathVariable UUID alertId) {
        return AlertResponse.from(alerts.get(alertId));
    }

    @PostMapping("/{alertId}/acknowledge")
    @PreAuthorize("hasAuthority('alert:acknowledge')")
    @Operation(summary = "Acknowledge an alert",
            description = "Stops the escalation timer and records who did it. Idempotent: "
                    + "acknowledging an already-acknowledged alert succeeds without changing it.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The acknowledged alert"),
            @ApiResponse(responseCode = "400", description = "The request is malformed"),
            @ApiResponse(responseCode = "403",
                    description = "Missing the alert:acknowledge permission"),
            @ApiResponse(responseCode = "404", description = "No such alert"),
            @ApiResponse(responseCode = "409", description = "The alert is already resolved")
    })
    public AlertResponse acknowledge(@PathVariable UUID alertId,
                                     @Valid @RequestBody AlertRequests.AcknowledgeRequest request,
                                     @AuthenticationPrincipal Jwt caller) {
        log.debug("Acknowledging {} [{}]", alertId, CorrelationId.currentOrNew());
        return AlertResponse.from(lifecycle.acknowledge(
                alertId, actorId(caller), actorName(caller), request.note()));
    }

    @PostMapping("/{alertId}/assign")
    @PreAuthorize("hasAuthority('alert:acknowledge')")
    @Operation(summary = "Assign an alert",
            description = "Assignment says 'this is yours'; acknowledgement says 'I have seen it'. "
                    + "Assigning does not stop the escalation timer, because handing work to "
                    + "somebody who has not read it is not attending to it.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The assigned alert"),
            @ApiResponse(responseCode = "400", description = "The request is malformed"),
            @ApiResponse(responseCode = "403",
                    description = "Missing the alert:acknowledge permission"),
            @ApiResponse(responseCode = "404", description = "No such alert"),
            @ApiResponse(responseCode = "409", description = "The alert is already resolved")
    })
    public AlertResponse assign(@PathVariable UUID alertId,
                                @Valid @RequestBody AlertRequests.AssignRequest request) {
        return AlertResponse.from(lifecycle.assign(alertId, request.assigneeId()));
    }

    @PostMapping("/{alertId}/resolve")
    @PreAuthorize("hasAuthority('alert:resolve')")
    @Operation(summary = "Resolve an alert",
            description = "Closes the alert with a disposition. The disposition feeds the "
                    + "true-positive ratio reporting uses to judge whether the scoring policy is "
                    + "calibrated, so INCONCLUSIVE is available rather than forcing a verdict.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The resolved alert"),
            @ApiResponse(responseCode = "400", description = "An unknown disposition"),
            @ApiResponse(responseCode = "403", description = "Missing the alert:resolve permission"),
            @ApiResponse(responseCode = "404", description = "No such alert"),
            @ApiResponse(responseCode = "409", description = "The alert is already resolved")
    })
    public AlertResponse resolve(@PathVariable UUID alertId,
                                 @Valid @RequestBody AlertRequests.ResolveRequest request,
                                 @AuthenticationPrincipal Jwt caller) {
        AlertDisposition disposition = AlertDisposition.valueOf(request.disposition());
        log.info("Resolving {} as {} [{}]", alertId, disposition, CorrelationId.currentOrNew());
        return AlertResponse.from(lifecycle.resolve(
                alertId, disposition, request.note(), actorId(caller), actorName(caller)));
    }

    static String actorId(Jwt caller) {
        return Optional.ofNullable(caller).map(Jwt::getSubject).orElse("unknown");
    }

    static String actorName(Jwt caller) {
        return Optional.ofNullable(caller)
                .map(jwt -> jwt.getClaimAsString("name"))
                .orElse("");
    }
}
