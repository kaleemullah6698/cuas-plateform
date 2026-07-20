package pk.nust.cuas.gateway.domain.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;
import pk.nust.cuas.commons.exception.DomainException;

/**
 * The shared edge state — rate-limit buckets and the revocation denylist — is unreachable.
 *
 * <p>The gateway <strong>fails closed</strong> on this condition and answers {@code 503}, because
 * both things it cannot read are controls rather than conveniences. Failing open would make the
 * rate limiter defeatable by whoever could disturb Redis, and would let a token revoked seconds
 * earlier be accepted. ARCHITECTURE.md section 4.1 already lists Redis as the gateway's datastore,
 * so an unreachable Redis is an unready gateway, not a degraded one.
 *
 * <p>Section 7.8 specifies the limits but not this failure mode; the choice is recorded here and
 * in the gateway README so it is visible rather than implicit.
 */
public class EdgeStateUnavailableException extends DomainException {

    /**
     * @param operation what was being attempted, for the log; never surfaced to the caller
     * @param cause     the underlying connectivity failure
     */
    public EdgeStateUnavailableException(String operation, Throwable cause) {
        super("edge-state-unavailable", HttpStatus.SERVICE_UNAVAILABLE,
                "The gateway cannot serve this request at present.",
                Map.of("operation", operation), cause);
    }
}
