package pk.nust.cuas.commons.correlation;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.ServletException;
import java.io.IOException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @AfterEach
    void clearMdc() {
        CorrelationId.clear();
    }

    @Test
    void adoptsInboundHeaderWhenPresent() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationId.HEADER, "inbound-id");
        MockHttpServletResponse response = new MockHttpServletResponse();
        CapturingChain chain = new CapturingChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.observedCorrelationId).isEqualTo("inbound-id");
        assertThat(response.getHeader(CorrelationId.HEADER)).isEqualTo("inbound-id");
    }

    @Test
    void generatesWhenHeaderAbsent() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        CapturingChain chain = new CapturingChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.observedCorrelationId).isNotBlank();
        assertThat(response.getHeader(CorrelationId.HEADER)).isEqualTo(chain.observedCorrelationId);
    }

    @Test
    void generatesWhenInboundHeaderIsOverlong() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationId.HEADER, "x".repeat(500));
        MockHttpServletResponse response = new MockHttpServletResponse();
        CapturingChain chain = new CapturingChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.observedCorrelationId).hasSizeLessThanOrEqualTo(128);
    }

    @Test
    void clearsCorrelationIdAfterRequest() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(CorrelationId.current()).isEmpty();
    }

    /** Captures the correlation ID that is visible to the downstream chain, mid-request. */
    private static final class CapturingChain extends MockFilterChain {
        private String observedCorrelationId;

        @Override
        public void doFilter(jakarta.servlet.ServletRequest request,
                             jakarta.servlet.ServletResponse response) {
            this.observedCorrelationId = CorrelationId.current().orElse(null);
        }
    }
}
