package pk.nust.cuas.gateway.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import pk.nust.cuas.commons.exception.ProblemDetailFactory;

/**
 * Renders a failed or missing authentication as RFC&nbsp;7807 rather than as Spring's default.
 *
 * <p>The reason is never told to the caller. Whether the token was expired, signed by an unknown
 * key, minted for another audience or explicitly revoked, the answer is the same 401 with the same
 * body: an attacker holding a token should learn nothing about <em>why</em> it was refused. The
 * distinction is logged server-side against the correlation ID, where an operator can see it.
 */
@Component
public class ProblemAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final Logger log =
            LoggerFactory.getLogger(ProblemAuthenticationEntryPoint.class);

    private final ProblemResponseWriter problemWriter;

    public ProblemAuthenticationEntryPoint(ProblemResponseWriter problemWriter) {
        this.problemWriter = problemWriter;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        log.warn("Rejected {} {}: {}", request.getMethod(), request.getRequestURI(),
                authException.getMessage());
        problemWriter.write(response, ProblemDetailFactory.create(
                HttpStatus.UNAUTHORIZED, "unauthenticated",
                "Valid authentication is required for this request."));
    }
}
