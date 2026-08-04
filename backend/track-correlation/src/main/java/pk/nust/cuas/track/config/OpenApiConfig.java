package pk.nust.cuas.track.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import org.springframework.context.annotation.Configuration;

/** OpenAPI document metadata for the track-correlation read API (ARCHITECTURE.md section 4.1.5). */
@Configuration
@OpenAPIDefinition(info = @Info(
        title = "Track Correlation API",
        version = "v1",
        description = "Read access to tracks built by correlating normalised detections.",
        license = @License(name = "CUAS Platform")))
public class OpenApiConfig {
}
