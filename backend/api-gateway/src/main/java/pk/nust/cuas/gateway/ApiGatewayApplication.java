package pk.nust.cuas.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Single ingress: TLS, JWT validation, rate limiting, WebSocket fan-out
 *
 * <p>See ARCHITECTURE.md section 4.1 for this service's responsibilities,
 * data ownership, published and consumed events, and scaling characteristics.
 *
 * <p>Scaffold only. No beans, endpoints, or persistence are wired yet.
 */
@SpringBootApplication
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
