package pk.nust.cuas.notification.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import org.springframework.context.annotation.Configuration;

/** OpenAPI document metadata for the notification-service API (ARCHITECTURE.md section 4.1.9). */
@Configuration
@OpenAPIDefinition(info = @Info(
        title = "Notification Service API",
        version = "v1",
        description = "A user's multi-channel notification preferences and quiet hours, their "
                + "delivery history, and a test send. Notifications are raised by consuming alert, "
                + "incident and sensor-health events; there is no endpoint that sends one to another "
                + "user.",
        license = @License(name = "CUAS Platform")))
public class OpenApiConfig {
}
