package pk.nust.cuas.threat.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import org.springframework.context.annotation.Configuration;

/** OpenAPI document metadata for the threat-assessment API (ARCHITECTURE.md section 4.1.7). */
@Configuration
@OpenAPIDefinition(info = @Info(
        title = "Threat Assessment API",
        version = "v1",
        description = "Composite threat scores, per-factor breakdowns, and the versioned scoring "
                + "policy. Recommendations are advisory only; no endpoint emits an engagement "
                + "action.",
        license = @License(name = "CUAS Platform")))
public class OpenApiConfig {
}
