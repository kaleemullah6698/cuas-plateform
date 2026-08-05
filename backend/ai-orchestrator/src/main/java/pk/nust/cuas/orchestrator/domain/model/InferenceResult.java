package pk.nust.cuas.orchestrator.domain.model;

import java.util.Optional;
import pk.nust.cuas.inference.v1.InferenceResponse;

/**
 * One service's answer, and how it was obtained (ARCHITECTURE.md sections 5.6, 6.5).
 *
 * <p>The response alone is not enough for the caller to act correctly: a mock response produced by a
 * fallback looks structurally identical to one produced because the service is configured as a mock.
 * Pairing the response with the {@link InferenceAttempt} that produced it is what lets the
 * orchestrator publish {@code ai.service.degraded.v1} for the first and not the second — and what
 * lets the operator's screen say which it was looking at.
 *
 * <p>Lives in {@code domain/model} despite naming a generated type because it is a value the
 * application layer passes around rather than a rule; the port it accompanies is likewise in
 * {@code application/}. The domain's <em>logic</em> — routing, policy, scenarios — stays contract-free.
 *
 * @param service   the service that was asked
 * @param response  its response, whoever ultimately produced it
 * @param attempt   whether the configured provider or the fallback answered
 * @param reason    why the fallback was used; present only when {@code attempt} is degraded
 */
public record InferenceResult(
        ServiceId service,
        InferenceResponse response,
        InferenceAttempt attempt,
        Optional<DegradationReason> reason) {

    public InferenceResult {
        if (service == null || response == null || attempt == null || reason == null) {
            throw new IllegalArgumentException("service, response, attempt and reason are required");
        }
        if (attempt.isDegraded() == reason.isEmpty()) {
            throw new IllegalArgumentException(
                    "a degraded result must carry a reason, and a primary result must not: "
                            + attempt + " with " + reason);
        }
    }

    /** A result the configured provider produced. */
    public static InferenceResult primary(ServiceId service, InferenceResponse response) {
        return new InferenceResult(service, response, InferenceAttempt.PRIMARY, Optional.empty());
    }

    /** A result the mock produced after the configured provider could not be used. */
    public static InferenceResult fallback(ServiceId service, InferenceResponse response,
                                           DegradationReason reason) {
        return new InferenceResult(
                service, response, InferenceAttempt.FALLBACK_MOCK, Optional.of(reason));
    }

    /** @return whether this answer came from a fallback rather than the configured provider */
    public boolean isDegraded() {
        return attempt.isDegraded();
    }
}
