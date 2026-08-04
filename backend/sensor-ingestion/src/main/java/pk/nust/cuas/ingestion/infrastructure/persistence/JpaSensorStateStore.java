package pk.nust.cuas.ingestion.infrastructure.persistence;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import pk.nust.cuas.ingestion.domain.model.HealthStatus;
import pk.nust.cuas.ingestion.domain.model.Modality;
import pk.nust.cuas.ingestion.domain.model.SensorHealthSnapshot;
import pk.nust.cuas.ingestion.domain.port.SensorStateStore;
import pk.nust.cuas.ingestion.infrastructure.persistence.entity.IngestionStatsEntity;
import pk.nust.cuas.ingestion.infrastructure.persistence.entity.SensorEntity;
import pk.nust.cuas.ingestion.infrastructure.persistence.entity.SensorHealthEntity;
import pk.nust.cuas.ingestion.infrastructure.persistence.repository.IngestionStatsJpaRepository;
import pk.nust.cuas.ingestion.infrastructure.persistence.repository.SensorHealthJpaRepository;
import pk.nust.cuas.ingestion.infrastructure.persistence.repository.SensorJpaRepository;

/**
 * JPA implementation of the {@link SensorStateStore} port over {@code sensordb}.
 *
 * <p>Writes are safe to serialise per sensor because a sensor's partition is owned by exactly one
 * consumer thread (ARCHITECTURE.md section 4.1.4), so the read-modify-write on the counters never
 * races within a sensor. A new sensor row is flushed before its dependent health and stats rows to
 * satisfy their foreign keys without relying on Hibernate's insert ordering.
 */
@Repository
public class JpaSensorStateStore implements SensorStateStore {

    private static final Logger log = LoggerFactory.getLogger(JpaSensorStateStore.class);

    private final SensorJpaRepository sensors;
    private final SensorHealthJpaRepository health;
    private final IngestionStatsJpaRepository stats;
    private final Clock clock;

    public JpaSensorStateStore(SensorJpaRepository sensors,
                               SensorHealthJpaRepository health,
                               IngestionStatsJpaRepository stats,
                               Clock clock) {
        this.sensors = sensors;
        this.health = health;
        this.stats = stats;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void recordAccepted(String sensorId, Modality modality, Instant ingestTime, long driftMs) {
        register(sensorId, modality, ingestTime);

        SensorHealthEntity healthRow = health.findById(sensorId)
                .orElseGet(() -> new SensorHealthEntity(sensorId, HealthStatus.ONLINE.name(), ingestTime));
        healthRow.recordDetection(ingestTime, driftMs, HealthStatus.ONLINE.name());
        health.save(healthRow);

        IngestionStatsEntity statsRow = statsRow(sensorId, ingestTime);
        statsRow.countNormalized(ingestTime);
        stats.save(statsRow);
    }

    @Override
    @Transactional
    public void recordDuplicate(String sensorId, Modality modality) {
        if (isBlank(sensorId)) {
            return;
        }
        Instant now = clock.instant();
        register(sensorId, modality, now);
        IngestionStatsEntity statsRow = statsRow(sensorId, now);
        statsRow.countDuplicate(now);
        stats.save(statsRow);
    }

    @Override
    @Transactional
    public void recordInvalid(String sensorId, Modality modality) {
        if (isBlank(sensorId)) {
            // A record with no sensor id cannot be attributed to a row; it is already logged.
            log.debug("Invalid detection has no sensor id; not attributing to a sensor row");
            return;
        }
        Instant now = clock.instant();
        register(sensorId, modality, now);
        IngestionStatsEntity statsRow = statsRow(sensorId, now);
        statsRow.countInvalid(now);
        stats.save(statsRow);
    }

    @Override
    @Transactional
    public void updateStatus(String sensorId, HealthStatus status) {
        health.findById(sensorId).ifPresent(row -> {
            row.updateStatus(status.name(), clock.instant());
            health.save(row);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public List<SensorHealthSnapshot> allHealth() {
        return health.findAll().stream()
                .map(row -> new SensorHealthSnapshot(
                        row.sensorId(),
                        HealthStatus.valueOf(row.status()),
                        Optional.ofNullable(row.lastDetectionAt()),
                        row.clockDriftMs()))
                .toList();
    }

    private void register(String sensorId, Modality modality, Instant at) {
        Optional<SensorEntity> existing = sensors.findById(sensorId);
        if (existing.isPresent()) {
            existing.get().observed(modality.name(), at);
            sensors.save(existing.get());
        } else {
            // Flush the new registry row before its dependent rows are written, so their foreign
            // keys resolve regardless of Hibernate's action ordering.
            sensors.saveAndFlush(new SensorEntity(sensorId, modality.name(), at));
        }
    }

    private IngestionStatsEntity statsRow(String sensorId, Instant now) {
        return stats.findById(sensorId).orElseGet(() -> new IngestionStatsEntity(sensorId, now));
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
