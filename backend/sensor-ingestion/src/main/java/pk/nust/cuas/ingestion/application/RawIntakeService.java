package pk.nust.cuas.ingestion.application;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import pk.nust.cuas.commons.correlation.CorrelationId;
import pk.nust.cuas.detection.v1.DetectionRecord;
import pk.nust.cuas.ingestion.application.port.RawDetectionPublisher;
import pk.nust.cuas.ingestion.domain.model.RawDetection;
import pk.nust.cuas.ingestion.domain.service.DetectionValidator;

/**
 * The intake half of the pipeline: accept a detection at the boundary, validate its header, and
 * publish it to {@code sensor.detection.raw.v1} (ARCHITECTURE.md section 4.1.4).
 *
 * <p>Shared by both intake adapters — the REST batch endpoint and the gRPC stream — so a detection
 * is validated and published identically however it arrives. Normalisation (dedup, clock
 * correction, identity, publication to the normalized topic) happens later, off the intake thread,
 * on the raw consumer; decoupling the two through the raw topic is what lets ingestion absorb a
 * burst without blocking a sensor (section 5.6).
 *
 * <p>The record is published verbatim but for one enrichment: if it carries no correlation id, one
 * is stamped from the current context so the whole pipeline — raw, normalized, health — can be
 * traced end to end. Everything the sensor sent is otherwise preserved for faithful replay.
 */
@Service
public class RawIntakeService {

    private static final Logger log = LoggerFactory.getLogger(RawIntakeService.class);

    private final DetectionValidator validator;
    private final RawDetectionPublisher rawPublisher;

    public RawIntakeService(DetectionValidator validator, RawDetectionPublisher rawPublisher) {
        this.validator = validator;
        this.rawPublisher = rawPublisher;
    }

    /**
     * Validates and, if valid, publishes one detection to the raw topic.
     *
     * @param record the canonical record as received at the boundary
     * @return the intake outcome
     */
    public IntakeResult accept(DetectionRecord record) {
        RawDetection projection = DetectionMapper.toRawDetection(record);
        List<String> violations = validator.validate(projection);
        if (!violations.isEmpty()) {
            log.warn("Rejecting detection from sensor '{}' seq={}: {}",
                    record.getSensorId(), record.getSensorSeq(), violations);
            return IntakeResult.rejected(record.getSensorSeq(), violations);
        }

        DetectionRecord toPublish = withCorrelationId(record);
        rawPublisher.publish(toPublish.getSensorId(), toPublish);
        log.debug("Accepted detection from sensor '{}' seq={} to raw topic",
                toPublish.getSensorId(), toPublish.getSensorSeq());
        return IntakeResult.accepted(record.getSensorSeq());
    }

    private DetectionRecord withCorrelationId(DetectionRecord record) {
        if (!record.getCorrelationId().isBlank()) {
            return record;
        }
        return record.toBuilder().setCorrelationId(CorrelationId.currentOrNew()).build();
    }
}
