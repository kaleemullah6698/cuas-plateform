package pk.nust.cuas.ingestion.infrastructure.messaging;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pk.nust.cuas.ingestion.application.HealthEmissionService;

/**
 * Drives {@link HealthEmissionService} on a fixed cadence.
 *
 * <p>The trigger lives in the infrastructure layer because scheduling is a delivery mechanism, not
 * a domain decision: the application service decides what health to emit, this decides when. The
 * interval is {@code ingestion.health.emit-interval}.
 */
@Component
public class HealthEmitterScheduler {

    private final HealthEmissionService healthEmission;

    public HealthEmitterScheduler(HealthEmissionService healthEmission) {
        this.healthEmission = healthEmission;
    }

    @Scheduled(fixedDelayString = "${ingestion.health.emit-interval:PT15S}")
    public void emit() {
        healthEmission.emitAll();
    }
}
