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
import org.springframework.http.HttpStatus;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pk.nust.cuas.alert.api.dto.IncidentDtos;
import pk.nust.cuas.alert.application.IncidentService;
import pk.nust.cuas.alert.domain.model.AlertDisposition;
import pk.nust.cuas.alert.domain.model.EvidenceKind;
import pk.nust.cuas.alert.domain.model.IncidentStatus;
import pk.nust.cuas.commons.correlation.CorrelationId;

/**
 * The incident API (ARCHITECTURE.md sections 4.1.8, 2.4.3).
 *
 * <p>Permissions are section 7.4's, unchanged: {@code incident:create}, {@code incident:read},
 * {@code incident:update}, {@code incident:close} — and {@code incident:close} is granted to ANALYST
 * and ADMIN only, not to OPERATOR. Reading the evidence bundle requires {@code evidence:export},
 * which is narrower still.
 *
 * <p><strong>The declaring and closing users come from the JWT.</strong> Section 2.4.3 records
 * incident declaration as a human act; a body-supplied declarer would let one operator open an
 * investigation in another's name and would make the decision chain's attribution worthless.
 *
 * <p>No endpoint anywhere in this controller accepts an engagement action, and the decision chain
 * has no field one could be written into. The platform emits advisory recommendations only.
 */
@RestController
@RequestMapping("/api/v1/incidents")
@Validated
@Tag(name = "Incidents", description = "Operator-declared investigations, their timeline and evidence")
public class IncidentController {

    private static final Logger log = LoggerFactory.getLogger(IncidentController.class);

    private final IncidentService incidents;

    public IncidentController(IncidentService incidents) {
        this.incidents = incidents;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('incident:read')")
    @Operation(summary = "List incidents", description = "Newest opened first.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The matching incidents"),
            @ApiResponse(responseCode = "400", description = "An invalid status or limit"),
            @ApiResponse(responseCode = "403", description = "Missing the incident:read permission")
    })
    public List<IncidentDtos.IncidentResponse> list(
            @RequestParam Optional<IncidentStatus> status,
            @RequestParam(defaultValue = "100") @Min(1) @Max(1000) int limit) {
        return incidents.list(status, limit).stream()
                .map(IncidentDtos.IncidentResponse::from)
                .toList();
    }

    @GetMapping("/{incidentId}")
    @PreAuthorize("hasAuthority('incident:read')")
    @Operation(summary = "Get an incident by id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The incident"),
            @ApiResponse(responseCode = "403", description = "Missing the incident:read permission"),
            @ApiResponse(responseCode = "404", description = "No such incident")
    })
    public IncidentDtos.IncidentResponse get(@PathVariable UUID incidentId) {
        return IncidentDtos.IncidentResponse.from(incidents.get(incidentId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('incident:create')")
    @Operation(summary = "Declare an incident",
            description = "Always a human act (section 2.4.3). The declaring user is taken from the "
                    + "authenticated token, never from the body. Audited fail-closed: a declaration "
                    + "that cannot be recorded does not happen.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "The declared incident"),
            @ApiResponse(responseCode = "400", description = "No alerts given, or a malformed body"),
            @ApiResponse(responseCode = "403", description = "Missing the incident:create permission"),
            @ApiResponse(responseCode = "404", description = "One of the alerts does not exist"),
            @ApiResponse(responseCode = "503", description = "The declaration could not be audited")
    })
    public IncidentDtos.IncidentResponse declare(
            @Valid @RequestBody IncidentDtos.DeclareIncidentRequest request,
            @AuthenticationPrincipal Jwt caller) {
        log.info("Declaring an incident from {} alert(s) [{}]",
                request.alertIds().size(), CorrelationId.currentOrNew());
        return IncidentDtos.IncidentResponse.from(incidents.declare(
                request.alertIds(), request.title(),
                AlertController.actorId(caller), AlertController.actorName(caller)));
    }

    @PostMapping("/{incidentId}/assign")
    @PreAuthorize("hasAuthority('incident:update')")
    @Operation(summary = "Assign an investigation")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The assigned incident"),
            @ApiResponse(responseCode = "403", description = "Missing the incident:update permission"),
            @ApiResponse(responseCode = "404", description = "No such incident"),
            @ApiResponse(responseCode = "409", description = "The incident is closed")
    })
    public IncidentDtos.IncidentResponse assign(
            @PathVariable UUID incidentId,
            @Valid @RequestBody IncidentDtos.AssignIncidentRequest request,
            @AuthenticationPrincipal Jwt caller) {
        return IncidentDtos.IncidentResponse.from(incidents.assign(
                incidentId, request.assigneeId(), request.assigneeName(),
                AlertController.actorId(caller)));
    }

    @PostMapping("/{incidentId}/decision")
    @PreAuthorize("hasAuthority('incident:update')")
    @Operation(summary = "Record the decision chain",
            description = "What the system advised, what the operator decided, and who authorised "
                    + "it (section 2.4.3). The engagement action is always NONE and is not a field: "
                    + "the platform produces advisory recommendations only.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The incident"),
            @ApiResponse(responseCode = "403", description = "Missing the incident:update permission"),
            @ApiResponse(responseCode = "404", description = "No such incident"),
            @ApiResponse(responseCode = "409", description = "The incident is closed")
    })
    public IncidentDtos.IncidentResponse recordDecision(
            @PathVariable UUID incidentId,
            @Valid @RequestBody IncidentDtos.RecordDecisionRequest request,
            @AuthenticationPrincipal Jwt caller) {
        return IncidentDtos.IncidentResponse.from(incidents.recordDecision(
                incidentId, request.operatorDecision(), AlertController.actorId(caller),
                AlertController.actorName(caller), request.auditRef()));
    }

    @PostMapping("/{incidentId}/close")
    @PreAuthorize("hasAuthority('incident:close')")
    @Operation(summary = "Close an investigation",
            description = "Section 7.4 grants incident:close to ANALYST and ADMIN only. Audited "
                    + "fail-closed.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The closed incident"),
            @ApiResponse(responseCode = "400", description = "An unknown disposition"),
            @ApiResponse(responseCode = "403", description = "Missing the incident:close permission"),
            @ApiResponse(responseCode = "404", description = "No such incident"),
            @ApiResponse(responseCode = "409", description = "The incident is already closed"),
            @ApiResponse(responseCode = "503", description = "The closure could not be audited")
    })
    public IncidentDtos.IncidentResponse close(
            @PathVariable UUID incidentId,
            @Valid @RequestBody IncidentDtos.CloseIncidentRequest request,
            @AuthenticationPrincipal Jwt caller) {
        return IncidentDtos.IncidentResponse.from(incidents.close(
                incidentId, AlertDisposition.valueOf(request.disposition()), request.summary(),
                AlertController.actorId(caller), AlertController.actorName(caller)));
    }

    @GetMapping("/{incidentId}/timeline")
    @PreAuthorize("hasAuthority('incident:read')")
    @Operation(summary = "Get an incident timeline",
            description = "Assembled from the platform's own events, so every line traces back to "
                    + "something that happened on a topic (section 2.4.3).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The timeline, oldest first"),
            @ApiResponse(responseCode = "403", description = "Missing the incident:read permission"),
            @ApiResponse(responseCode = "404", description = "No such incident")
    })
    public List<IncidentDtos.TimelineEntryResponse> timeline(@PathVariable UUID incidentId) {
        return incidents.timeline(incidentId).stream()
                .map(IncidentDtos.TimelineEntryResponse::from)
                .toList();
    }

    @GetMapping("/{incidentId}/notes")
    @PreAuthorize("hasAuthority('incident:read')")
    @Operation(summary = "Get an incident's notes")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The notes, oldest first"),
            @ApiResponse(responseCode = "403", description = "Missing the incident:read permission"),
            @ApiResponse(responseCode = "404", description = "No such incident")
    })
    public List<IncidentDtos.NoteResponse> notes(@PathVariable UUID incidentId) {
        return incidents.notes(incidentId).stream()
                .map(IncidentDtos.NoteResponse::from)
                .toList();
    }

    @PostMapping("/{incidentId}/notes")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('incident:update')")
    @Operation(summary = "Append a note",
            description = "Append-only and attributed: notes form part of the exported "
                    + "investigative record, and one that could be edited afterwards is not "
                    + "evidence.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "The note"),
            @ApiResponse(responseCode = "400", description = "An empty note"),
            @ApiResponse(responseCode = "403", description = "Missing the incident:update permission"),
            @ApiResponse(responseCode = "404", description = "No such incident")
    })
    public IncidentDtos.NoteResponse addNote(
            @PathVariable UUID incidentId,
            @Valid @RequestBody IncidentDtos.AddNoteRequest request,
            @AuthenticationPrincipal Jwt caller) {
        return IncidentDtos.NoteResponse.from(incidents.addNote(
                incidentId, request.body(),
                AlertController.actorId(caller), AlertController.actorName(caller)));
    }

    @GetMapping("/{incidentId}/evidence")
    @PreAuthorize("hasAuthority('evidence:export')")
    @Operation(summary = "Get an incident's evidence bundle",
            description = "References with content hashes, never payloads. Section 7.4 grants "
                    + "evidence:export to ANALYST, AUDITOR and ADMIN only.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The evidence references"),
            @ApiResponse(responseCode = "403", description = "Missing the evidence:export permission"),
            @ApiResponse(responseCode = "404", description = "No such incident")
    })
    public List<IncidentDtos.EvidenceResponse> evidence(@PathVariable UUID incidentId) {
        return incidents.evidence(incidentId).stream()
                .map(IncidentDtos.EvidenceResponse::from)
                .toList();
    }

    @PostMapping("/{incidentId}/evidence")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('incident:update')")
    @Operation(summary = "Attach an evidence reference",
            description = "The object is verified to exist and to hash as claimed before the "
                    + "reference is recorded — a bundle assembled from unverified references would "
                    + "fail when a reviewer asks for it.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "The evidence reference"),
            @ApiResponse(responseCode = "400",
                    description = "An unknown kind, or an object that is missing or does not match "
                            + "its hash"),
            @ApiResponse(responseCode = "403", description = "Missing the incident:update permission"),
            @ApiResponse(responseCode = "404", description = "No such incident")
    })
    public IncidentDtos.EvidenceResponse attachEvidence(
            @PathVariable UUID incidentId,
            @Valid @RequestBody IncidentDtos.AttachEvidenceRequest request) {
        return IncidentDtos.EvidenceResponse.from(incidents.attachEvidence(
                incidentId, EvidenceKind.valueOf(request.kind()), request.uri(), request.sha256(),
                request.contentType(), request.sourceId()));
    }
}
