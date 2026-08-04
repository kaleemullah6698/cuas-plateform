package pk.nust.cuas.ingestion.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import pk.nust.cuas.detection.v1.DetectionRecord;
import pk.nust.cuas.ingestion.application.port.NormalizedDetectionPublisher;
import pk.nust.cuas.ingestion.domain.model.NormalizationOutcome;
import pk.nust.cuas.ingestion.domain.model.RawDetection;
import pk.nust.cuas.ingestion.domain.port.ClockDriftStore;
import pk.nust.cuas.ingestion.domain.port.DedupWindow;
import pk.nust.cuas.ingestion.domain.port.SensorStateStore;
import pk.nust.cuas.ingestion.domain.service.ClockDriftEstimator;
import pk.nust.cuas.ingestion.domain.service.DetectionValidator;
import pk.nust.cuas.ingestion.domain.service.Uuidv7Generator;

/**
 * The normalisation half of the pipeline: consume a raw detection, bring it to the canonical form
 * every downstream consumer relies on, and publish it to {@code sensor.detection.normalized.v1}
 * (ARCHITECTURE.md section 4.1.4).
 *
 * <p>Normalisation is four steps: validate the header (defence in depth — the raw topic is not only
 * fed by this service's own intake); drop the record if its {@code (sensorId, sensorSeq)} pair has
 * already been seen; correct the clock by folding {@code ingestTime - sensorTime} into the sensor's
 * running drift estimate while preserving the reported {@code sensorTime} unchanged; and assign a
 * time-sortable UUIDv7 identity and the authoritative {@code ingestTime}. Only then is the record
 * published and the sensor's health and counters advanced.
 *
 * <p>Publication happens before the state write on purpose: under the section 5.6 principle that
 * ingestion must never lose a detection, it is better to have published and under-counted than to
 * have recorded and not published. The dedup window makes at-least-once redelivery safe.
 */
@Service
public class NormalizationService {

    /** Carried in-band on every normalised record so a consumer can branch on it (section 5.5). */
    static final String SCHEMA_VERSION = "1.0.0";

    private static final Logger log = LoggerFactory.getLogger(NormalizationService.class);

    private final DetectionValidator validator;
    private final DedupWindow dedupWindow;
    private final ClockDriftStore driftStore;
    private final ClockDriftEstimator driftEstimator;
    private final Uuidv7Generator idGenerator;
    private final NormalizedDetectionPublisher normalizedPublisher;
    private final SensorStateStore stateStore;
    private final Clock clock;

    public NormalizationService(DetectionValidator validator,
                                DedupWindow dedupWindow,
                                ClockDriftStore driftStore,
                                ClockDriftEstimator driftEstimator,
                                Uuidv7Generator idGenerator,
                                NormalizedDetectionPublisher normalizedPublisher,
                                SensorStateStore stateStore,
                                Clock clock) {
        this.validator = validator;
        this.dedupWindow = dedupWindow;
        this.driftStore = driftStore;
        this.driftEstimator = driftEstimator;
        this.idGenerator = idGenerator;
        this.normalizedPublisher = normalizedPublisher;
        this.stateStore = stateStore;
        this.clock = clock;
    }

    /**
     * Normalises one raw record, applying all side effects (publication, counters, health).
     *
     * @param raw the record consumed from {@code sensor.detection.raw.v1}
     * @return the outcome, returned so callers and tests can observe the decision
     */
    public NormalizationOutcome normalize(DetectionRecord raw) {
        RawDetection projection = DetectionMapper.toRawDetection(raw);

        List<String> violations = validator.validate(projection);
        if (!violations.isEmpty()) {
            log.warn("Discarding invalid raw detection from sensor '{}' seq={}: {}",
                    raw.getSensorId(), raw.getSensorSeq(), violations);
            stateStore.recordInvalid(projection.sensorId(), projection.modality());
            return new NormalizationOutcome.Invalid(violations);
        }

        if (dedupWindow.seenBefore(projection.sensorId(), projection.sensorSeq())) {
            log.debug("Dropping duplicate detection from sensor '{}' seq={}",
                    projection.sensorId(), projection.sensorSeq());
            stateStore.recordDuplicate(projection.sensorId(), projection.modality());
            return new NormalizationOutcome.Duplicate();
        }

        Instant ingestTime = clock.instant();
        long driftMs = correctedDrift(projection, ingestTime);
        UUID detectionId = idGenerator.generate(ingestTime);

        DetectionRecord normalized = raw.toBuilder()
                .setDetectionId(detectionId.toString())
                .setIngestTime(DetectionMapper.toTimestamp(ingestTime))
                .setClockDriftMs(driftMs)
                .setSchemaVersion(SCHEMA_VERSION)
                .build();

        // Publish first, then mark seen: a publish failure throws here and leaves the record
        // unmarked, so the redelivery is normalised again rather than dropped as a duplicate.
        normalizedPublisher.publish(projection.sensorId(), normalized);
        dedupWindow.markSeen(projection.sensorId(), projection.sensorSeq());
        stateStore.recordAccepted(projection.sensorId(), projection.modality(), ingestTime, driftMs);
        log.debug("Normalised detection from sensor '{}' seq={} as {} (drift {} ms)",
                projection.sensorId(), projection.sensorSeq(), detectionId, driftMs);
        return new NormalizationOutcome.Normalized(detectionId, ingestTime, driftMs);
    }

    private long correctedDrift(RawDetection projection, Instant ingestTime) {
        long sampleMs = ingestTime.toEpochMilli() - projection.sensorTime().orElseThrow().toEpochMilli();
        long previousMs = driftStore.currentDriftMs(projection.sensorId());
        long updatedMs = driftEstimator.next(previousMs, sampleMs);
        driftStore.update(projection.sensorId(), updatedMs);
        return updatedMs;
    }
}
