package pk.nust.cuas.simulator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Development sensor adapter for the CUAS platform (ARCHITECTURE.md section 4.1.4).
 *
 * <p>Stands in for real sensors during development: it generates synthetic detections and drives
 * them into sensor-ingestion through the <em>real</em> REST and gRPC intake endpoints — never by
 * writing to Kafka or the database directly. That constraint is what makes it a faithful test of the
 * ingestion path rather than a shortcut around it.
 *
 * <p>Runs headless (no web server); it is a client, not a service. A simulation runs on startup only
 * when {@code simulator.run-on-startup} is set, so booting the module does not fire traffic
 * unexpectedly.
 */
@SpringBootApplication
public class SensorSimulatorApplication {

    public static void main(String[] args) {
        SpringApplication.run(SensorSimulatorApplication.class, args);
    }
}
