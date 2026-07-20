package pk.nust.cuas.gateway.support;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A minimal upstream service, so routing can be asserted end to end.
 *
 * <p>Built on the JDK's own HTTP server rather than a mock-server library: the gateway's job is to
 * speak HTTP to something real, and adding a dependency to prove it would be a poor trade for one
 * class of this size.
 */
public final class StubUpstream implements AutoCloseable {

    private final HttpServer server;
    private final Map<String, String> lastRequest = new ConcurrentHashMap<>();

    public StubUpstream() {
        try {
            this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException ex) {
            throw new IllegalStateException("Could not start the stub upstream", ex);
        }
        server.createContext("/", exchange -> {
            lastRequest.put("method", exchange.getRequestMethod());
            lastRequest.put("path", exchange.getRequestURI().getPath());
            String correlationId = exchange.getRequestHeaders().getFirst("X-Correlation-Id");
            lastRequest.put("correlationId", correlationId == null ? "" : correlationId);
            String authorization = exchange.getRequestHeaders().getFirst("Authorization");
            lastRequest.put("authorization", authorization == null ? "" : authorization);
            lastRequest.put("body",
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));

            byte[] response = "{\"upstream\":\"ok\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
    }

    /** @return the base URI to configure as a route target */
    public String uri() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /**
     * @param field one of {@code method}, {@code path}, {@code correlationId},
     *              {@code authorization}, {@code body}
     * @return the recorded value, or {@code null} when no request has arrived
     */
    public String lastRequest(String field) {
        return lastRequest.get(field);
    }

    /** @return whether any request has reached the upstream */
    public boolean received() {
        return !lastRequest.isEmpty();
    }

    /** Forgets the recorded request. */
    public void reset() {
        lastRequest.clear();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
