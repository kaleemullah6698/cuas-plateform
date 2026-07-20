package pk.nust.cuas.gateway.config;

import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;

import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RequestPredicates;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;
import pk.nust.cuas.commons.correlation.CorrelationId;

/**
 * Reverse-proxy routes to the backend services (ARCHITECTURE.md section 4.1.1).
 *
 * <p>Paths are passed through unchanged rather than rewritten. A request that arrives as
 * {@code /api/v1/users/42} reaches user-service as {@code /api/v1/users/42}, so a path in a log,
 * an audit record or a bug report means the same thing at every hop. Rewriting at the edge is the
 * usual reason a path in an audit trail cannot be found in a service's own logs.
 *
 * <p>The correlation ID is attached to every proxied request. The commons filter has already
 * established it for the inbound leg; adding it here carries it across the hop so the downstream
 * service adopts the same value rather than minting its own.
 */
@Configuration
@EnableConfigurationProperties(GatewayRouteProperties.class)
public class RouteConfig {

    private static final Logger log = LoggerFactory.getLogger(RouteConfig.class);

    /**
     * @param properties the declared routing table
     * @return one composed router function covering every declared prefix
     */
    @Bean
    public RouterFunction<ServerResponse> platformRoutes(GatewayRouteProperties properties) {
        RouterFunction<ServerResponse> composed = null;

        for (GatewayRouteProperties.Route declared : properties.routes()) {
            URI uri = URI.create(declared.uri());
            for (String prefix : declared.prefixes()) {
                RouterFunction<ServerResponse> single = route(declared.id() + ":" + prefix)
                        // Both the collection itself and everything beneath it: "/api/v1/users"
                        // and "/api/v1/users/42" must reach the same upstream.
                        .route(RequestPredicates.path(prefix)
                                .or(RequestPredicates.path(prefix + "/**")), http(uri))
                        .before(request -> org.springframework.web.servlet.function.ServerRequest
                                .from(request)
                                .header(CorrelationId.HEADER, CorrelationId.currentOrNew())
                                .build())
                        .build();
                composed = composed == null ? single : composed.and(single);
                log.info("Routing {} -> {} ({})", prefix, declared.uri(), declared.id());
            }
        }

        // A gateway with no declared routes still serves its own health, metrics and API docs;
        // an empty router function keeps the context valid rather than failing startup.
        return composed == null
                ? route("no-routes-declared").build()
                : composed;
    }
}
