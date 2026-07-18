package pk.nust.cuas.ingestion;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Sensor payload acceptance and normalisation to DetectionRecord
 *
 * <p>See ARCHITECTURE.md section 4.1 for this service's responsibilities,
 * data ownership, published and consumed events, and scaling characteristics.
 *
 * <p>Scaffold only. No beans, endpoints, or persistence are wired yet.
 */
@SpringBootApplication
public class SensorIngestionApplication {

    public static void main(String[] args) {
        SpringApplication.run(SensorIngestionApplication.class, args);
    }
}
