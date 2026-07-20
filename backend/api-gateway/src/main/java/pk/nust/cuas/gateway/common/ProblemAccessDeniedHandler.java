package pk.nust.cuas.gateway.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import pk.nust.cuas.commons.exception.ProblemDetailFactory;

/**
 * Renders an authorisation failure as RFC&nbsp;7807.
 *
 * <p>Complements {@link pk.nust.cuas.gateway.api.filter.PermissionPreCheckFilter}, which produces
 * its own 403 naming the permission required. This handler covers the denials the pre-check has no
 * rule for, and deliberately names nothing: it does not know which permission would have
 * sufficed, and guessing would be worse than silence.
 */
@Component
public class ProblemAccessDeniedHandler implements AccessDeniedHandler {

    private static final Logger log = LoggerFactory.getLogger(ProblemAccessDeniedHandler.class);

    private final ProblemResponseWriter problemWriter;

    public ProblemAccessDeniedHandler(ProblemResponseWriter problemWriter) {
        this.problemWriter = problemWriter;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        log.warn("Denied {} {}: {}", request.getMethod(), request.getRequestURI(),
                accessDeniedException.getMessage());
        problemWriter.write(response, ProblemDetailFactory.create(
                HttpStatus.FORBIDDEN, "access-denied",
                "You are not permitted to perform this action."));
    }
}
