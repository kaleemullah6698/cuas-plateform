package pk.nust.cuas.alert;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Alert lifecycle, escalation, incidents and evidence bundles (ARCHITECTURE.md section 4.1.8).
 *
 * <p>Consumes {@code threat.level.changed.v1} and raises alerts from it; deduplicates recurrences
 * into a suppression window; escalates unacknowledged alerts on a Redis sorted-set schedule with
 * leader election; closes alerts whose track was lost or merged; and owns operator-declared
 * incidents with their timelines, notes and evidence. Owns {@code alertdb}.
 *
 * <p><strong>Two properties this service exists to hold.</strong> Duplicate alerts are impossible,
 * not merely unlikely — a partial unique index on the deduplication key is what enforces it, because
 * section 5.7 names them as the failure that destroys operator trust faster than any other. And
 * every recommendation it relays is advisory: the vocabulary is section 6.3.4's closed five, the
 * decision chain's engagement action has exactly one legal value, and no endpoint accepts one.
 *
 * <p>{@code @EnableScheduling} drives the escalation sweep. The alert and incident Spring Data
 * repositories are grouped as nested interfaces, so {@code considerNestedRepositories} is enabled;
 * the annotation on the root class keeps the default base-package scan covering the whole service.
 */
@SpringBootApplication
@EnableScheduling
@EnableJpaRepositories(considerNestedRepositories = true)
public class AlertServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AlertServiceApplication.class, args);
    }
}
