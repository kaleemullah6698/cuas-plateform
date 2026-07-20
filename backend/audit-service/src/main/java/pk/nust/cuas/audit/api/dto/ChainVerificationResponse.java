package pk.nust.cuas.audit.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.HexFormat;
import java.util.UUID;
import pk.nust.cuas.audit.domain.service.ChainVerificationResult;

/**
 * Result of verifying a segment of the audit chain.
 *
 * <p>A broken chain is the highest-severity condition the platform can report (section 4.1.11),
 * so the response names the first offending record precisely rather than returning a bare boolean.
 */
@Schema(description = "Outcome of an audit chain verification")
public record ChainVerificationResponse(
        @Schema(description = "Whether the segment verified intact") boolean intact,
        @Schema(description = "How many records were walked") int recordsVerified,
        @Schema(description = "Chain head after the segment, hex") String headHash,
        @Schema(description = "Position of the first broken record") Long brokenAtSeq,
        @Schema(description = "Identity of the first broken record") UUID brokenAtAuditId,
        @Schema(description = "What was wrong") String reason) {

    /**
     * @param result the domain result
     * @return its API representation
     */
    public static ChainVerificationResponse from(ChainVerificationResult result) {
        return new ChainVerificationResponse(
                result.intact(),
                result.recordsVerified(),
                result.headHash() == null ? null : HexFormat.of().formatHex(result.headHash()),
                result.brokenAtSeq(),
                result.brokenAtAuditId(),
                result.reason());
    }
}
