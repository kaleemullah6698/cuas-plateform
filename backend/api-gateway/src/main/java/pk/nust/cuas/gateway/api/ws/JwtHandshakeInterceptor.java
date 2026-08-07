package pk.nust.cuas.gateway.api.ws;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

/**
 * Authenticates the WebSocket upgrade (ARCHITECTURE.md §7.2 WebSocket row, §7.3).
 *
 * <p>A browser cannot set an {@code Authorization} header on a WebSocket, so the access token arrives
 * on the upgrade URL as {@code ?token=…}. It is validated by the <em>same</em> JWKS-backed decoder as
 * every other request — including the revocation check (§7.3) — so a revoked token is refused at the
 * door. On success the subject and permissions are put on the session attributes for the handler; on
 * failure the upgrade is refused with 401 and no session is created. The token never reaches a log:
 * the access-log filter records the path only, and the query string is not logged.
 */
@Component
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    private static final Logger log = LoggerFactory.getLogger(JwtHandshakeInterceptor.class);

    static final String USER_ID = "userId";
    static final String PERMISSIONS = "permissions";

    private final JwtDecoder jwtDecoder;

    public JwtHandshakeInterceptor(JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String token = tokenOf(request);
        if (token == null || token.isBlank()) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        try {
            Jwt jwt = jwtDecoder.decode(token);
            attributes.put(USER_ID, jwt.getSubject());
            attributes.put(PERMISSIONS, permissions(jwt));
            return true;
        } catch (JwtException ex) {
            log.debug("Refusing a WebSocket upgrade with an invalid token", ex);
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // No post-handshake work.
    }

    private static String tokenOf(ServerHttpRequest request) {
        if (request instanceof ServletServerHttpRequest servletRequest) {
            return servletRequest.getServletRequest().getParameter("token");
        }
        return null;
    }

    private static Set<String> permissions(Jwt jwt) {
        List<String> perms = jwt.getClaimAsStringList("perms");
        return perms == null ? Set.of() : Set.copyOf(perms);
    }
}
