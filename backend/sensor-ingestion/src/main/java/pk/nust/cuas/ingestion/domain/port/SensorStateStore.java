package pk.nust.cuas.ingestion.domain.port;

import java.time.Instant;
import java.util.List;
import pk.nust.cuas.ingestion.domain.model.HealthStatus;
import pk.nust.cuas.ingestion.domain.model.Modality;
import pk.nust.cuas.ingestion.domain.model.SensorHealthSnapshot;

/**
 * The durable per-sensor state this service owns: the sensor registry, its health, and its
 * ingestion counters (ARCHITECTURE.md section 4.1.4, {@code sensordb}).
 *
 * <p>Declared in the domain as a repository port; the JPA implementation lives in
 * {@code infrastructure/persistence}. The three record-* methods are the only writes the pipeline
 * makes, one per normalisation outcome, so the counters and the health row always move together
 * with the decision that caused them.
 */
public interface SensorStateStore {

    /**
     * Records an accepted detection: registers the sensor if new, advances its health to the given
     * ingest time and drift, and increments both the received and normalized counters.
     *
     * @param sensorId   the reporting sensor
     * @param modality   its last observed modality
     * @param ingestTime the platform clock at normalisation
     * @param driftMs    the current clock-drift estimate
     */
    void recordAccepted(String sensorId, Modality modality, Instant ingestTime, long driftMs);

    /**
     * Records a detection dropped as a duplicate: registers the sensor if new and increments the
     * received and duplicate counters. Health liveness is not advanced — a replayed record is not
     * fresh evidence the sensor is reporting now.
     *
     * @param sensorId the reporting sensor
     * @param modality its modality
     */
    void recordDuplicate(String sensorId, Modality modality);

    /**
     * Records a detection rejected by validation: registers the sensor if the id is usable and
     * increments the received and invalid counters.
     *
     * @param sensorId the reporting sensor
     * @param modality its modality
     */
    void recordInvalid(String sensorId, Modality modality);

    /**
     * Persists a sensor's recomputed liveness, so the stored health reflects the last emission
     * rather than only the last accepted detection.
     *
     * @param sensorId the sensor
     * @param status   the recomputed status
     */
    void updateStatus(String sensorId, HealthStatus status);

    /**
     * @return a health snapshot for every known sensor, for the periodic health emission
     */
    List<SensorHealthSnapshot> allHealth();
}
