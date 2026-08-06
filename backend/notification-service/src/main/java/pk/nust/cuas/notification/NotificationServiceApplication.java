package pk.nust.cuas.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Notification-service (ARCHITECTURE.md section 4.1.9): multi-channel delivery, per-user channel
 * preferences and quiet hours, retry with backoff, cross-channel deduplication, and delivery receipt
 * tracking.
 *
 * <p>Consumes {@code alert.raised.v1}, {@code alert.escalated.v1}, {@code incident.created.v1} and
 * {@code sensor.health.v1}; publishes {@code notification.sent.v1} and {@code notification.failed.v1};
 * owns {@code notifydb}. Scheduling is enabled for the retry sweep in {@link
 * pk.nust.cuas.notification.config.NotificationServiceConfig}.
 */
@SpringBootApplication
public class NotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
