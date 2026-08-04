package pk.nust.cuas.ingestion.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI document metadata for the sensor-ingestion REST surface (ARCHITECTURE.md section 4.1.4).
 *
 * <p>The document describes only the HTTP intake; the gRPC {@code StreamDetections} contract is
 * defined in {@code ingestion.proto}, which is the authoritative description of that surface.
 */
@Configuration
@OpenAPIDefinition(info = @Info(
        title = "Sensor Ingestion API",
        version = "v1",
        description = "Accepts sensor detections and normalises them to the canonical DetectionRecord.",
        license = @License(name = "CUAS Platform")))
public class OpenApiConfig {
}
