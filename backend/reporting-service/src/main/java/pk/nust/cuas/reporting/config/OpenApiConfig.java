package pk.nust.cuas.reporting.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import org.springframework.context.annotation.Configuration;

/** OpenAPI document metadata for the reporting-service API (ARCHITECTURE.md §4.1.10). */
@Configuration
@OpenAPIDefinition(info = @Info(
        title = "Reporting Service API",
        version = "v1",
        description = "Templated, asynchronously-generated reports (incident, daily summary, sensor "
                + "performance, fusion efficacy) exported as PDF, JSON or CSV. Generation is accepted "
                + "with a 202 and a job id; the artifact is downloaded once the job succeeds.",
        license = @License(name = "CUAS Platform")))
public class OpenApiConfig {
}
