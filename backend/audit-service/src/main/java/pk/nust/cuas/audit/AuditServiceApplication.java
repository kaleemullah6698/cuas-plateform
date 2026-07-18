package pk.nust.cuas.audit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Append-only hash-chained tamper-evident audit log
 *
 * <p>See ARCHITECTURE.md section 4.1 for this service's responsibilities,
 * data ownership, published and consumed events, and scaling characteristics.
 *
 * <p>Scaffold only. No beans, endpoints, or persistence are wired yet.
 */
@SpringBootApplication
public class AuditServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuditServiceApplication.class, args);
    }
}
