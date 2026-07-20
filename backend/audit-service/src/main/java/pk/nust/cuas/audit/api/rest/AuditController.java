package pk.nust.cuas.audit.api.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pk.nust.cuas.audit.api.dto.AuditRecordResponse;
import pk.nust.cuas.audit.api.dto.ChainVerificationResponse;
import pk.nust.cuas.audit.domain.AuditRepository;
import pk.nust.cuas.audit.domain.model.AuditRecord;
import pk.nust.cuas.audit.domain.model.HashChain;
import pk.nust.cuas.audit.domain.service.ChainVerificationResult;
import pk.nust.cuas.audit.domain.service.ChainVerifier;

/**
 * Auditor-facing API over the audit log (ARCHITECTURE.md section 4.1.11).
 *
 * <p>Every endpoint is permission-gated per the section 7.4 matrix. Note that the read and verify
 * permissions are held only by AUDITOR and ADMIN — and that ADMIN deliberately cannot export.
 *
 * <p>There is intentionally no write endpoint. Records arrive only by consuming
 * {@code platform.audit.v1}; exposing an HTTP append would let a caller with network access
 * insert records outside the event flow.
 */
@RestController
@RequestMapping("/api/v1/audit")
@Validated
@Tag(name = "Audit", description = "Query and verify the tamper-evident audit log")
public class AuditController {

    private static final Logger log = LoggerFactory.getLogger(AuditController.class);

    private final AuditRepository repository;
    private final ChainVerifier chainVerifier;

    public AuditController(AuditRepository repository, ChainVerifier chainVerifier) {
        this.repository = repository;
        this.chainVerifier = chainVerifier;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('audit:read')")
    @Operation(summary = "Query the audit log",
            description = "Returns matching records, most recent first. All filters are optional.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Matching records"),
            @ApiResponse(responseCode = "400", description = "Invalid query parameters"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token"),
            @ApiResponse(responseCode = "403", description = "Caller lacks audit:read")
    })
    public ResponseEntity<List<AuditRecordResponse>> search(
            @Parameter(description = "Restrict to this actor")
            @RequestParam(required = false) String actorId,
            @Parameter(description = "Restrict to this action, e.g. LOGIN_SUCCEEDED")
            @RequestParam(required = false) String action,
            @Parameter(description = "Restrict to this resource type, e.g. USER")
            @RequestParam(required = false) String resourceType,
            @Parameter(description = "Earliest occurredAt, inclusive (ISO-8601)")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @Parameter(description = "Latest occurredAt, inclusive (ISO-8601)")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @Parameter(description = "Maximum rows to return")
            @RequestParam(defaultValue = "50") @Min(1) @Max(500) int limit,
            @Parameter(description = "Rows to skip")
            @RequestParam(defaultValue = "0") @Min(0) int offset) {

        log.debug("Audit query: actor={} action={} resourceType={} limit={}",
                actorId, action, resourceType, limit);

        List<AuditRecordResponse> results =
                repository.search(actorId, action, resourceType, from, to, limit, offset).stream()
                        .map(AuditRecordResponse::from)
                        .toList();
        return ResponseEntity.ok(results);
    }

    @PostMapping("/verify")
    @PreAuthorize("hasAuthority('audit:verify')")
    @Operation(summary = "Verify a segment of the chain",
            description = "Walks the segment and reports the first broken link, if any. "
                    + "Verifying from the beginning of the log requires no preceding hash.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Verification completed; see 'intact'"),
            @ApiResponse(responseCode = "400", description = "Invalid range"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token"),
            @ApiResponse(responseCode = "403", description = "Caller lacks audit:verify")
    })
    public ResponseEntity<ChainVerificationResponse> verify(
            @Parameter(description = "Lowest seq to verify, inclusive")
            @RequestParam(defaultValue = "1") @Min(1) long fromSeq,
            @Parameter(description = "Highest seq to verify, inclusive; defaults to the chain head")
            @RequestParam(required = false) Long toSeq) {

        long upperBound = toSeq != null ? toSeq : Long.MAX_VALUE;
        if (upperBound < fromSeq) {
            throw new IllegalArgumentException("toSeq must be greater than or equal to fromSeq");
        }

        List<AuditRecord> segment = repository.findSegment(fromSeq, upperBound);
        byte[] expectedPrevHash = precedingHash(fromSeq);

        ChainVerificationResult result = chainVerifier.verify(segment, expectedPrevHash);
        if (result.intact()) {
            log.info("Audit chain verified intact: {} records from seq {}",
                    result.recordsVerified(), fromSeq);
        } else {
            // The single highest-severity condition this platform can report (section 4.1.11).
            log.error("AUDIT CHAIN BROKEN at seq={} auditId={}: {}",
                    result.brokenAtSeq(), result.brokenAtAuditId(), result.reason());
        }
        return ResponseEntity.ok(ChainVerificationResponse.from(result));
    }

    /**
     * Resolves the hash the first record of the segment must chain to: the genesis hash when the
     * segment starts at the beginning of the log, otherwise the preceding record's hash.
     */
    private byte[] precedingHash(long fromSeq) {
        if (fromSeq <= 1) {
            return HashChain.GENESIS_HASH;
        }
        return repository.findSegment(fromSeq - 1, fromSeq - 1).stream()
                .findFirst()
                .map(AuditRecord::recordHash)
                .orElse(HashChain.GENESIS_HASH);
    }
}
