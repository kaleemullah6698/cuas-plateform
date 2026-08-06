package pk.nust.cuas.threat.api.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.Instant;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pk.nust.cuas.commons.correlation.CorrelationId;
import pk.nust.cuas.threat.api.dto.EvaluateRequest;
import pk.nust.cuas.threat.api.dto.PolicyResponse;
import pk.nust.cuas.threat.api.dto.PolicyUpdateRequest;
import pk.nust.cuas.threat.api.dto.ThreatAssessmentResponse;
import pk.nust.cuas.threat.api.dto.ThreatBreakdownResponse;
import pk.nust.cuas.threat.application.ThreatPolicyService;
import pk.nust.cuas.threat.application.ThreatQueryService;

/**
 * The threat-assessment API (ARCHITECTURE.md section 4.1.7).
 *
 * <p><strong>No new permission was invented for these endpoints.</strong> Section 7.4's matrix is
 * closed — adding a permission is an architecture change — so each operation maps onto an existing
 * one: a threat score is a fact about a track, so reads use {@code track:read}, which every role
 * holds; the policy surface uses {@code policy:read} and {@code policy:write}, and section 7.4 grants
 * {@code policy:write} to ADMIN alone. That grant is right for what the endpoint does: republishing
 * a policy changes what the system concludes about every track in the airspace.
 *
 * <p>The what-if is a read. It evaluates a policy against a hypothetical and persists nothing, so
 * {@code policy:read} is the honest permission — requiring {@code policy:write} would mean an
 * operator had to be able to change a policy in order to ask what one would do.
 */
@RestController
@RequestMapping("/threats")
@Validated
@Tag(name = "Threats", description = "Composite threat scores, breakdowns and scoring policy")
public class ThreatController {

    private static final Logger log = LoggerFactory.getLogger(ThreatController.class);

    /** The single policy this service scores against until config-service owns policies (M8). */
    private static final String POLICY_ID = "default";

    private final ThreatQueryService threats;
    private final ThreatPolicyService policies;
    private final Clock clock;

    public ThreatController(ThreatQueryService threats, ThreatPolicyService policies, Clock clock) {
        this.threats = threats;
        this.policies = policies;
        this.clock = clock;
    }

    @GetMapping("/{trackId}")
    @PreAuthorize("hasAuthority('track:read')")
    @Operation(summary = "Get a track's current threat assessment",
            description = "The latest composite score, level and advisory recommendation. The level"
                    + " is null when no factor could be evaluated — an unassessed track is not a"
                    + " LOW one.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The assessment"),
            @ApiResponse(responseCode = "400", description = "The track id is not a UUID"),
            @ApiResponse(responseCode = "403", description = "Missing the track:read permission"),
            @ApiResponse(responseCode = "404", description = "The track has never been scored")
    })
    public ThreatAssessmentResponse get(@PathVariable UUID trackId) {
        log.debug("Reading threat assessment for track {} [{}]", trackId, CorrelationId.currentOrNew());
        return ThreatAssessmentResponse.from(threats.latest(trackId));
    }

    @GetMapping("/{trackId}/breakdown")
    @PreAuthorize("hasAuthority('track:read')")
    @Operation(summary = "Get a track's threat-score breakdown",
            description = "Every weighted factor behind the score, including the ones that had no"
                    + " evidence and why — the explainability panel of section 2.4.2.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The breakdown"),
            @ApiResponse(responseCode = "400", description = "The track id is not a UUID"),
            @ApiResponse(responseCode = "403", description = "Missing the track:read permission"),
            @ApiResponse(responseCode = "404", description = "The track has never been scored")
    })
    public ThreatBreakdownResponse breakdown(@PathVariable UUID trackId) {
        log.debug("Reading threat breakdown for track {} [{}]", trackId, CorrelationId.currentOrNew());
        return ThreatBreakdownResponse.from(threats.latest(trackId));
    }

    @GetMapping("/policies")
    @PreAuthorize("hasAuthority('policy:read')")
    @Operation(summary = "List scoring policy versions",
            description = "Every published version, newest effective first. The version in force is"
                    + " the first entry.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The policy versions"),
            @ApiResponse(responseCode = "403", description = "Missing the policy:read permission")
    })
    public List<PolicyResponse> policies() {
        return policies.versions().stream().map(PolicyResponse::from).toList();
    }

    @PutMapping("/policies")
    @PreAuthorize("hasAuthority('policy:write')")
    @Operation(summary = "Publish a new scoring policy version",
            description = "Creates a new immutable version and makes it active. Published versions"
                    + " are never edited, because every assessment cites the version that produced"
                    + " it. Audited fail-closed: a change that cannot be recorded does not happen.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The published version"),
            @ApiResponse(responseCode = "400", description = "The request is malformed"),
            @ApiResponse(responseCode = "403", description = "Missing the policy:write permission"),
            @ApiResponse(responseCode = "422", description = "The policy is not valid — weights that"
                    + " do not sum to 1.0, thresholds out of order, or a reused version label"),
            @ApiResponse(responseCode = "503", description = "The change could not be audited and"
                    + " was therefore not applied")
    })
    public PolicyResponse publishPolicy(@Valid @RequestBody PolicyUpdateRequest request,
                                        @AuthenticationPrincipal Jwt caller) {
        String actorId = Optional.ofNullable(caller).map(Jwt::getSubject).orElse("unknown");
        log.info("Publishing threat policy version {} requested by {} [{}]",
                request.version(), actorId, CorrelationId.currentOrNew());
        return PolicyResponse.from(policies.publish(
                request.toPolicy(POLICY_ID, Instant.now(clock)), actorId, request.justification()));
    }

    @PostMapping("/evaluate")
    @PreAuthorize("hasAuthority('policy:read')")
    @Operation(summary = "Score hypothetical evidence against a policy",
            description = "Answers what a policy would conclude, without persisting or publishing"
                    + " anything. Use it to check a policy change before imposing it on live"
                    + " traffic.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The assessment that would result"),
            @ApiResponse(responseCode = "400", description = "The request is malformed"),
            @ApiResponse(responseCode = "403", description = "Missing the policy:read permission"),
            @ApiResponse(responseCode = "422", description = "The named policy version does not exist")
    })
    public ThreatBreakdownResponse evaluate(@Valid @RequestBody EvaluateRequest request) {
        Instant now = Instant.now(clock);
        return ThreatBreakdownResponse.from(policies.evaluate(
                request.toEvidence(now), Optional.ofNullable(request.policyVersion())));
    }
}
