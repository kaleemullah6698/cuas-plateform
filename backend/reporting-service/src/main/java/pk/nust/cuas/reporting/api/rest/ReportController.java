package pk.nust.cuas.reporting.api.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pk.nust.cuas.commons.correlation.CorrelationId;
import pk.nust.cuas.reporting.api.dto.ReportDtos.GenerateReportRequest;
import pk.nust.cuas.reporting.api.dto.ReportDtos.ReportJobResponse;
import pk.nust.cuas.reporting.api.dto.ReportDtos.ReportTemplateResponse;
import pk.nust.cuas.reporting.api.dto.ReportDtos.ReportTemplatesResponse;
import pk.nust.cuas.reporting.application.ReportService;
import pk.nust.cuas.reporting.domain.model.ReportJob;

/**
 * The reporting API (ARCHITECTURE.md §4.1.10).
 *
 * <p>Permissions come from §7.4: {@code report:read} to list templates, poll a job and download an
 * artifact; {@code report:generate} to request one. Generation is accepted with a 202 and a job id —
 * the request thread never waits on it — and the artifact is downloaded once the job has succeeded.
 */
@RestController
@RequestMapping("/reports")
@Tag(name = "Reports", description = "Templated asynchronous report generation and download")
public class ReportController {

    private static final Logger log = LoggerFactory.getLogger(ReportController.class);

    private final ReportService reports;
    private final ObjectMapper objectMapper;

    public ReportController(ReportService reports, ObjectMapper objectMapper) {
        this.reports = reports;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/templates")
    @PreAuthorize("hasAuthority('report:read')")
    @Operation(summary = "List the report templates the service can generate")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The templates"),
            @ApiResponse(responseCode = "403", description = "Missing the report:read permission")
    })
    public ReportTemplatesResponse templates() {
        return new ReportTemplatesResponse(reports.templates().stream()
                .map(ReportTemplateResponse::from).toList());
    }

    @PostMapping("/generate")
    @PreAuthorize("hasAuthority('report:generate')")
    @Operation(summary = "Request a report",
            description = "Accepts the request and returns 202 with the job id. Generation runs "
                    + "asynchronously; poll the job for its outcome and download the artifact once it "
                    + "has succeeded.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "The accepted job"),
            @ApiResponse(responseCode = "400", description = "An unknown type or unsupported format"),
            @ApiResponse(responseCode = "403", description = "Missing the report:generate permission")
    })
    public ResponseEntity<ReportJobResponse> generate(@Valid @RequestBody GenerateReportRequest request,
                                                      @AuthenticationPrincipal Jwt caller) {
        JsonNode parameters = request.parameters() == null || request.parameters().isNull()
                ? objectMapper.createObjectNode() : request.parameters();
        String correlationId = CorrelationId.currentOrNew();
        log.info("Accepting {} report ({}) for {} [{}]", request.type(), request.format(),
                caller.getSubject(), correlationId);
        ReportJob job = reports.submit(request.type(), request.format(), parameters,
                caller.getSubject(), correlationId);
        return ResponseEntity.accepted().body(ReportJobResponse.from(job));
    }

    @GetMapping("/jobs/{reportId}")
    @PreAuthorize("hasAuthority('report:read')")
    @Operation(summary = "Get a report job's status")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The job"),
            @ApiResponse(responseCode = "403", description = "Missing the report:read permission"),
            @ApiResponse(responseCode = "404", description = "No such job")
    })
    public ReportJobResponse job(@PathVariable UUID reportId) {
        return reports.job(reportId).map(ReportJobResponse::from).orElseThrow(
                () -> new ReportNotFoundException("no report job " + reportId));
    }

    @GetMapping("/{reportId}/download")
    @PreAuthorize("hasAuthority('report:read')")
    @Operation(summary = "Download a generated report's artifact")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The artifact"),
            @ApiResponse(responseCode = "403", description = "Missing the report:read permission"),
            @ApiResponse(responseCode = "404", description = "No such job"),
            @ApiResponse(responseCode = "409", description = "The report has not succeeded yet")
    })
    public ResponseEntity<byte[]> download(@PathVariable UUID reportId) {
        return reports.download(reportId)
                .map(download -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(download.format().contentType()))
                        .header(HttpHeaders.CONTENT_DISPOSITION,
                                "attachment; filename=\"" + download.filename() + "\"")
                        .body(download.content()))
                .orElseGet(() -> {
                    ReportJob job = reports.job(reportId).orElseThrow(
                            () -> new ReportNotFoundException("no report job " + reportId));
                    throw new ReportNotReadyException("report " + reportId + " is " + job.status());
                });
    }
}
