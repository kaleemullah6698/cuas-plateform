package pk.nust.cuas.gateway.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;
import pk.nust.cuas.commons.exception.ProblemDetailFactory;
import pk.nust.cuas.gateway.domain.exception.EdgeStateUnavailableException;

/**
 * Renders a {@link EdgeStateUnavailableException} raised anywhere in the filter chain as
 * RFC&nbsp;7807.
 *
 * <p>{@code @RestControllerAdvice} cannot reach this. The gateway's controls — the rate limiter
 * and the revocation denylist — run as servlet filters and inside the JWT decoder, all of which
 * execute before the dispatcher servlet, so an exception from them would otherwise escape to the
 * container and be answered with an HTML error page. Registered outermost but inside the access
 * log, so the rendered status is still counted and logged.
 */
public class DomainExceptionFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(DomainExceptionFilter.class);

    private final ProblemResponseWriter problemWriter;

    public DomainExceptionFilter(ProblemResponseWriter problemWriter) {
        this.problemWriter = problemWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            filterChain.doFilter(request, response);
        } catch (EdgeStateUnavailableException ex) {
            // Fail closed. The gateway would rather refuse a request than serve it with its rate
            // limits and revocation checks silently switched off.
            log.error("Refusing {} {}: the gateway's shared edge state is unavailable",
                    request.getMethod(), request.getRequestURI(), ex);
            problemWriter.write(response, ProblemDetailFactory.from(ex));
        }
    }
}
