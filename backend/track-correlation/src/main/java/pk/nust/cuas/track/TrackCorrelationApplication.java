package pk.nust.cuas.track;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Detection association, Kalman state estimation, track lifecycle, CPA
 *
 * <p>See ARCHITECTURE.md section 4.1 for this service's responsibilities,
 * data ownership, published and consumed events, and scaling characteristics.
 *
 * <p>Scaffold only. No beans, endpoints, or persistence are wired yet.
 */
@SpringBootApplication
public class TrackCorrelationApplication {

    public static void main(String[] args) {
        SpringApplication.run(TrackCorrelationApplication.class, args);
    }
}
