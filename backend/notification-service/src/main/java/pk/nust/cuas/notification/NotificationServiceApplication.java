package pk.nust.cuas.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Multi-channel delivery, preferences, delivery receipts
 *
 * <p>See ARCHITECTURE.md section 4.1 for this service's responsibilities,
 * data ownership, published and consumed events, and scaling characteristics.
 *
 * <p>Scaffold only. No beans, endpoints, or persistence are wired yet.
 */
@SpringBootApplication
public class NotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
