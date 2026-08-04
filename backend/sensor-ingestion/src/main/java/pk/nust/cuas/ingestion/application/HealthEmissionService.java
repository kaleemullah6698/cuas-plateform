package pk.nust.cuas.ingestion.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import pk.nust.cuas.ingestion.application.port.SensorHealthPublisher;
import pk.nust.cuas.ingestion.domain.model.HealthStatus;
import pk.nust.cuas.ingestion.domain.model.SensorHealthSnapshot;
import pk.nust.cuas.ingestion.domain.policy.HealthStatusPolicy;
import pk.nust.cuas.ingestion.domain.port.SensorStateStore;

/**
 * Emits per-sensor health telemetry on a fixed cadence (ARCHITECTURE.md section 4.1.4).
 *
 * <p>Health is a derived, time-sensitive fact: a sensor is offline once it has been quiet for long
 * enough, which no detection will ever announce — its silence is the signal. So health cannot be
 * published only in reaction to traffic; it is recomputed on a timer from each sensor's
 * last-seen time and published whether or not the sensor is still reporting. The recomputed status
 * is written back so the stored health reflects the last emission.
 *
 * <p>The cadence itself is a scheduling concern and lives in the infrastructure layer; this service
 * holds only the decision of what to emit, so it can be tested without a scheduler.
 */
@Service
public class HealthEmissionService {

    private static final Logger log = LoggerFactory.getLogger(HealthEmissionService.class);

    private final SensorStateStore stateStore;
    private final HealthStatusPolicy healthPolicy;
    private final SensorHealthPublisher publisher;
    private final Clock clock;

    public HealthEmissionService(SensorStateStore stateStore,
                                 HealthStatusPolicy healthPolicy,
                                 SensorHealthPublisher publisher,
                                 Clock clock) {
        this.stateStore = stateStore;
        this.healthPolicy = healthPolicy;
        this.publisher = publisher;
        this.clock = clock;
    }

    /** Recomputes and publishes health for every known sensor. */
    public void emitAll() {
        Instant now = clock.instant();
        List<SensorHealthSnapshot> sensors = stateStore.allHealth();
        for (SensorHealthSnapshot stored : sensors) {
            HealthStatus status = healthPolicy.evaluate(stored.lastDetectionAt(), now);
            stateStore.updateStatus(stored.sensorId(), status);
            publisher.publish(new SensorHealthSnapshot(
                    stored.sensorId(), status, stored.lastDetectionAt(), stored.clockDriftMs()));
        }
        if (!sensors.isEmpty()) {
            log.debug("Emitted health for {} sensor(s)", sensors.size());
        }
    }
}
