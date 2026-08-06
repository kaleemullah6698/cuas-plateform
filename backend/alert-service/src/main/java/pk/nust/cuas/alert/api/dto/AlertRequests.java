package pk.nust.cuas.alert.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The request bodies of the alert lifecycle endpoints (ARCHITECTURE.md section 4.1.8).
 *
 * <p>Grouped in one file because each is two or three fields and they are only ever read together
 * with the controller that consumes them.
 */
public final class AlertRequests {

    private AlertRequests() {
    }

    /**
     * {@code POST /alerts/{id}/acknowledge}.
     *
     * <p>The acknowledging actor is <em>not</em> a field. It is taken from the authenticated JWT, so
     * a caller cannot acknowledge on somebody else's behalf — which matters because an
     * acknowledgement stops the escalation timer and the audit chain has to be able to say who did
     * that (section 7.5).
     *
     * @param note optional free text
     */
    @Schema(description = "Acknowledge an alert, stopping its escalation timer")
    public record AcknowledgeRequest(@Size(max = 1000) String note) {
    }

    /**
     * {@code POST /alerts/{id}/assign}.
     *
     * @param assigneeId the user the alert is assigned to
     */
    @Schema(description = "Assign an alert without acknowledging it")
    public record AssignRequest(@NotBlank @Size(max = 128) String assigneeId) {
    }

    /**
     * {@code POST /alerts/{id}/resolve}.
     *
     * @param disposition TRUE_POSITIVE | FALSE_POSITIVE | BENIGN | DUPLICATE | INCONCLUSIVE
     * @param note        optional free text
     */
    @Schema(description = "Resolve an alert with a disposition")
    public record ResolveRequest(
            @NotBlank String disposition,
            @Size(max = 1000) String note) {
    }
}
