package pk.nust.cuas.gateway.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI description of the endpoints the gateway itself serves.
 *
 * <p>Scope is deliberate: this documents the gateway, not the platform. Proxied routes are
 * described by the services that own them, and aggregating their specifications here would create
 * a second copy that drifts from the first the moment a service changes.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    /** @return the gateway's API description */
    @Bean
    public OpenAPI gatewayOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("CUAS API Gateway")
                        .version("v1")
                        .description("""
                                Single authenticated ingress for the Counter-UAS platform
                                (ARCHITECTURE.md section 4.1.1).

                                Every request other than authentication and the probes requires a
                                bearer access token issued by auth-service. The gateway validates
                                it against a cached JWKS, rejects revoked tokens, applies the
                                section 7.8 rate limits, performs the section 7.4 permission
                                pre-check, and proxies the request unchanged to the owning service.

                                Errors are RFC 7807 `application/problem+json` and carry a
                                `correlationId` that appears in the logs of every service that
                                handled the request.
                                """))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("RS256 access token, 15-minute lifetime "
                                        + "(section 7.3)")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
