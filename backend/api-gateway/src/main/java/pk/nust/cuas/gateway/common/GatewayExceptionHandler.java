package pk.nust.cuas.gateway.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import pk.nust.cuas.commons.exception.BaseExceptionHandler;
import pk.nust.cuas.commons.exception.ProblemDetailFactory;

/**
 * Renders errors from the gateway's own endpoints as RFC&nbsp;7807 (CLAUDE.md section 4).
 *
 * <p>Inherits the platform-wide handlers from {@link BaseExceptionHandler} so the gateway's errors
 * are shaped exactly like every service's, and adds the one case peculiar to a reverse proxy: an
 * upstream that cannot be reached.
 *
 * <p>This advice covers the gateway's own controllers. Rejections made in the filter chain —
 * rate limiting, authentication, the permission pre-check — never reach a controller advice and
 * are rendered by {@link ProblemResponseWriter} instead.
 */
@RestControllerAdvice
public class GatewayExceptionHandler extends BaseExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GatewayExceptionHandler.class);

    /**
     * An upstream service could not be reached or did not answer in time.
     *
     * <p>Answered as {@code 502}, not {@code 500}: the gateway itself is working, and an operator
     * reading the status should be pointed at the service behind it. The upstream's address is
     * logged but never returned — internal topology is not a client's business.
     *
     * @param ex the connectivity failure
     * @return the problem
     */
    @ExceptionHandler({java.net.ConnectException.class, java.net.SocketTimeoutException.class,
            org.springframework.web.client.ResourceAccessException.class})
    public ProblemDetail onUpstreamUnreachable(Exception ex) {
        log.error("Upstream service unreachable: {}", ex.getMessage(), ex);
        return ProblemDetailFactory.create(HttpStatus.BAD_GATEWAY, "upstream-unavailable",
                "The service handling this request is unavailable.");
    }
}
