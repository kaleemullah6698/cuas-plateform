package pk.nust.cuas.threat.infrastructure.messaging.consumer;

import com.google.protobuf.InvalidProtocolBufferException;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.Optional;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import pk.nust.cuas.inference.v1.InferenceResponse;
import pk.nust.cuas.inference.v1.InferenceStatus;
import pk.nust.cuas.inference.v1.Prediction;
import pk.nust.cuas.threat.application.ThreatAssessmentService;
import pk.nust.cuas.threat.common.ThreatProtoMapper;
import pk.nust.cuas.threat.domain.model.InferenceObservation;
import pk.nust.cuas.threat.infrastructure.messaging.ThreatTopics;

/**
 * Consumes {@code ai.inference.completed.v1} and re-scores the track (ARCHITECTURE.md sections
 * 4.1.7, 6.3.4).
 *
 * <p><strong>This is the entire coupling between this service and the AI boundary.</strong> Section
 * 4.1.7 places the weighted rules engine here in Track A; the {@code threat-scorer} residual and
 * every other model reach this service as an ordinary event, weighed as one factor of five. There is
 * no gRPC client in this module and no synchronous call to the orchestrator — CLAUDE.md section 2
 * keeps {@code track-correlation → ai-orchestrator} the pipeline's only synchronous link, and an
 * inference call on this service's critical path would make a degraded model boundary a degraded
 * threat picture.
 *
 * <p>The <em>calibrated</em> confidence is preferred over the raw score. Section 6.2's
 * {@code ConfidenceReport} exists precisely so confidences are comparable across services, and
 * sensor agreement compares them — an uncalibrated score would make one model's habitual optimism
 * look like corroboration.
 */
@Component
public class InferenceCompletedConsumer {

    private static final Logger log = LoggerFactory.getLogger(InferenceCompletedConsumer.class);

    private final ThreatAssessmentService assessments;
    private final ThreatProtoMapper mapper;
    private final Clock clock;

    public InferenceCompletedConsumer(ThreatAssessmentService assessments, ThreatProtoMapper mapper,
                                      Clock clock) {
        this.assessments = assessments;
        this.mapper = mapper;
        this.clock = clock;
    }

    @KafkaListener(topics = ThreatTopics.INFERENCE_COMPLETED,
            groupId = "${spring.kafka.consumer.group-id:threat-assessment}")
    public void onInferenceCompleted(ConsumerRecord<String, byte[]> record) {
        try {
            InferenceResponse response = InferenceResponse.parseFrom(record.value());
            ThreatProtoMapper.parseUuid(response.getTrackId()).ifPresentOrElse(
                    trackId -> toObservation(response).ifPresentOrElse(
                            observation -> assessments.onInference(trackId, observation),
                            () -> log.debug("Inference for track {} carries no usable verdict",
                                    response.getTrackId())),
                    () -> log.warn("Skipping inference result with an unusable track id at offset {}",
                            record.offset()));
        } catch (InvalidProtocolBufferException ex) {
            log.warn("Skipping unparseable inference result at offset {}", record.offset(), ex);
        }
    }

    /**
     * @return the observation, or empty when the result carries nothing to weigh — a failure status,
     *         or a model that suppressed every prediction below its threshold. Folding a
     *         label-less result into the evidence would count it towards sensor agreement, letting a
     *         model that declined to answer vote on what the answer was.
     */
    private Optional<InferenceObservation> toObservation(InferenceResponse response) {
        if (!isUsable(response.getStatus()) || response.getPredictionsCount() == 0) {
            return Optional.empty();
        }
        Prediction top = response.getPredictionsList().stream()
                .min(Comparator.comparingInt(Prediction::getRank))
                .orElseThrow();
        double confidence = response.hasConfidence() && response.getConfidence().getCalibratedScore() > 0
                ? response.getConfidence().getCalibratedScore()
                : top.getConfidence();
        return Optional.of(mapper.toObservation(
                response.getModelId(), response.getModelVersion(),
                top.getLabel(), top.getLabelClass(), confidence, Instant.now(clock)));
    }

    /**
     * A degraded result — the mock answering for an open circuit breaker (section 6.5) — counts.
     * It is a real verdict, badged {@code 0.0.0-mock} so the breakdown can say where it came from,
     * and discarding it would mean a threat picture that goes blank exactly when the AI boundary is
     * struggling.
     */
    private static boolean isUsable(InferenceStatus status) {
        return status == InferenceStatus.STATUS_OK
                || status == InferenceStatus.STATUS_LOW_CONFIDENCE
                || status == InferenceStatus.STATUS_DEGRADED;
    }
}
