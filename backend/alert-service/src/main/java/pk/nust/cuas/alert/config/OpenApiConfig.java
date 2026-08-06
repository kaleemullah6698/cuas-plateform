package pk.nust.cuas.alert.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import org.springframework.context.annotation.Configuration;

/** OpenAPI document metadata for the alert-service API (ARCHITECTURE.md section 4.1.8). */
@Configuration
@OpenAPIDefinition(info = @Info(
        title = "Alert Service API",
        version = "v1",
        description = "The operator alert queue, its lifecycle, and operator-declared "
                + "investigations with their timelines and evidence bundles. Recommendations "
                + "relayed here are advisory only; no endpoint accepts or emits an engagement "
                + "action.",
        license = @License(name = "CUAS Platform")))
public class OpenApiConfig {
}
