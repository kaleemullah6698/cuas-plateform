package pk.nust.cuas.commons.correlation;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Establishes a correlation ID for every inbound HTTP request.
 *
 * <p>If the request carries an {@value CorrelationId#HEADER} header the value is adopted;
 * otherwise a new identifier is generated. The value is bound to the {@link CorrelationId}
 * (and therefore the logging MDC) for the duration of the request, echoed back on the response
 * so a caller can correlate, and cleared in a {@code finally} block so it never leaks to the
 * next request served by the same worker thread.
 *
 * <p>Ordered {@link Ordered#HIGHEST_PRECEDENCE} so the correlation ID is present before any
 * other filter or handler logs.
 */
public final class CorrelationIdFilter extends OncePerRequestFilter implements Ordered {

    private static final int MAX_ACCEPTED_LENGTH = 128;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String correlationId = resolve(request.getHeader(CorrelationId.HEADER));
        CorrelationId.set(correlationId);
        response.setHeader(CorrelationId.HEADER, correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            CorrelationId.clear();
        }
    }

    /**
     * Adopts a supplied header value when present and well-formed, otherwise generates one.
     *
     * <p>An over-long inbound value is rejected in favour of a fresh identifier: the header is
     * attacker-controllable and a correlation ID flows into logs, so its length is bounded.
     */
    private String resolve(String headerValue) {
        if (headerValue == null || headerValue.isBlank() || headerValue.length() > MAX_ACCEPTED_LENGTH) {
            return CorrelationId.generate();
        }
        return headerValue;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
