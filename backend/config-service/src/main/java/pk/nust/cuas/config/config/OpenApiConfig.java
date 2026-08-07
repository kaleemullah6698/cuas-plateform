package pk.nust.cuas.config.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import org.springframework.context.annotation.Configuration;

/** OpenAPI document metadata for the config-service API (ARCHITECTURE.md §4.1.12). */
@Configuration
@OpenAPIDefinition(info = @Info(
        title = "Configuration Service API",
        version = "v1",
        description = "Runtime configuration without redeploy: namespaced values with a full version "
                + "history, before/after change auditing, and rollback. Changes are pushed to services "
                + "over the gRPC WatchConfig stream and mirrored on the platform.config.* topics.",
        license = @License(name = "CUAS Platform")))
public class OpenApiConfig {
}
