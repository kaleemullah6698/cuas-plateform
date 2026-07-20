package pk.nust.cuas.gateway.api.filter;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * A request whose body has been read into memory so it can be read again.
 *
 * <p>Needed for exactly one case: section 7.8 limits {@code POST /auth/login} per account as well
 * as per address, and the account name is in the body. A servlet body is a one-shot stream, so
 * reading it in a filter would leave nothing for the proxied request.
 *
 * <p>Used only on the login path and only up to {@link #MAX_BUFFERED_BYTES}. Buffering every
 * request body at the edge would give an attacker a memory-exhaustion lever, and buffering an
 * unbounded login body would give them the same lever on a smaller surface. A body larger than the
 * cap is left unbuffered and simply unattributed to an account — it falls back to the per-address
 * limit, which still applies.
 */
final class BufferedBodyRequest extends HttpServletRequestWrapper {

    /** Generous for a login payload, far too small to be worth attacking. */
    static final int MAX_BUFFERED_BYTES = 8 * 1024;

    private final byte[] body;

    private BufferedBodyRequest(HttpServletRequest request, byte[] body) {
        super(request);
        this.body = body;
    }

    /**
     * Buffers the request body, or returns the request unchanged when it is too large.
     *
     * @param request the request to buffer
     * @return a re-readable request, or the original when the body exceeds the cap
     * @throws IOException when the body cannot be read
     */
    static HttpServletRequest buffering(HttpServletRequest request) throws IOException {
        byte[] buffered = request.getInputStream().readNBytes(MAX_BUFFERED_BYTES + 1);
        if (buffered.length > MAX_BUFFERED_BYTES) {
            return request;
        }
        return new BufferedBodyRequest(request, buffered);
    }

    /** @return the buffered body */
    byte[] body() {
        return body.clone();
    }

    @Override
    public ServletInputStream getInputStream() {
        ByteArrayInputStream source = new ByteArrayInputStream(body);
        return new ServletInputStream() {
            @Override
            public boolean isFinished() {
                return source.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener readListener) {
                throw new UnsupportedOperationException(
                        "The gateway reads buffered login bodies synchronously");
            }

            @Override
            public int read() {
                return source.read();
            }
        };
    }

    @Override
    public BufferedReader getReader() {
        return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
    }
}
