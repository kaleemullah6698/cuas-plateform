package pk.nust.cuas.gateway.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * The routing table, declared in configuration rather than in code.
 *
 * <p>Upstream addresses differ per environment — {@code localhost} on a workstation, a compose
 * service name in development, a cluster DNS name later — so hard-coding them would put an
 * environment detail in a compiled artefact. What must not vary, the permission required for a
 * route, is compiled in instead (see {@code RoutePermissionPolicy}); the split is deliberate.
 *
 * <p>Routes are validated at startup: a blank upstream or an empty prefix fails the context rather
 * than producing a gateway that silently cannot reach a service.
 *
 * @param routes the declared routes; may be empty, which yields a gateway that proxies nothing
 */
@Validated
@ConfigurationProperties(prefix = "gateway")
public record GatewayRouteProperties(@Valid List<Route> routes) {

    public GatewayRouteProperties {
        routes = routes == null ? List.of() : List.copyOf(routes);
    }

    /**
     * One upstream service and the path prefixes routed to it.
     *
     * @param id       identifier used in logs and metrics, conventionally the service name
     * @param uri      base URI of the upstream, for example {@code http://user-service:8082}
     * @param prefixes path prefixes routed to this upstream, for example {@code /api/v1/users}
     */
    public record Route(
            @NotBlank String id,
            @NotBlank String uri,
            @NotEmpty List<@NotBlank String> prefixes) {
    }
}
