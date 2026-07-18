package pk.nust.cuas.threat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Composite threat scoring, rules engine, advisory recommendations
 *
 * <p>See ARCHITECTURE.md section 4.1 for this service's responsibilities,
 * data ownership, published and consumed events, and scaling characteristics.
 *
 * <p>Scaffold only. No beans, endpoints, or persistence are wired yet.
 */
@SpringBootApplication
public class ThreatAssessmentApplication {

    public static void main(String[] args) {
        SpringApplication.run(ThreatAssessmentApplication.class, args);
    }
}
