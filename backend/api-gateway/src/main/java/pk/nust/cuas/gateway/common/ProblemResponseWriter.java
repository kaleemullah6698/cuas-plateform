package pk.nust.cuas.gateway.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

/**
 * Renders an RFC&nbsp;7807 problem directly onto a servlet response.
 *
 * <p>The gateway needs this where a {@code @RestControllerAdvice} cannot help. Rate limiting,
 * permission pre-check and authentication all reject inside the servlet filter chain, which runs
 * before the dispatcher servlet — an exception thrown there never reaches an exception handler, and
 * the container's default error page would answer in HTML. Every rejection the gateway makes must
 * look the same to a client as one made by a service behind it, so it is written here by hand.
 */
@Component
public class ProblemResponseWriter {

    private final ObjectMapper objectMapper;

    public ProblemResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Writes the problem as {@code application/problem+json}, committing the response.
     *
     * @param response the response to write to
     * @param problem  the problem to render; its status becomes the response status
     * @throws IOException when the response cannot be written
     */
    public void write(HttpServletResponse response, ProblemDetail problem) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(problem.getStatus());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), problem);
        response.flushBuffer();
    }
}
