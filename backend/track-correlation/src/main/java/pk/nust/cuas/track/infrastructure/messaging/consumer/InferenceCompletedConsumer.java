package pk.nust.cuas.track.infrastructure.messaging.consumer;

import com.google.protobuf.InvalidProtocolBufferException;
import java.time.Instant;
import java.util.Comparator;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import pk.nust.cuas.inference.v1.InferenceResponse;
import pk.nust.cuas.inference.v1.InferenceStatus;
import pk.nust.cuas.inference.v1.Prediction;
import pk.nust.cuas.track.application.TrackClassificationService;
import pk.nust.cuas.track.domain.model.TrackClassification;
import pk.nust.cuas.track.infrastructure.messaging.TrackTopics;

/**
 * Consumes {@code ai.inference.completed.v1} and applies the result to its track (ARCHITECTURE.md
 * section 4.1.5).
 *
 * <p>The asynchronous half of classification. The synchronous {@code Infer} call enriches a track
 * immediately with the single best answer; this topic carries <em>every</em> service's result,
 * including ones that arrived after the call's deadline or were produced for a track this instance
 * did not ask about. §4.1.5 lists both as inputs, and this is what makes a late or out-of-band
 * inference still reach the track it belongs to.
 *
 * <p><b>A malformed or unusable message is acknowledged, not retried.</b> A message that cannot be
 * parsed will not parse on the next attempt either, and blocking the partition on it would stop every
 * later classification behind one poison record — the failure mode §5.6 exists to prevent. It is
 * logged and skipped; DLQ routing is the M8 hardening item already recorded for the other consumers.
 */
@Component
public class InferenceCompletedConsumer {

    private static final Logger log = LoggerFactory.getLogger(InferenceCompletedConsumer.class);

    private final TrackClassificationService classifications;

    public InferenceCompletedConsumer(TrackClassificationService classifications) {
        this.classifications = classifications;
    }

    @KafkaListener(topics = TrackTopics.INFERENCE_COMPLETED,
            groupId = "${spring.kafka.consumer.group-id:track-correlation}")
    public void onInferenceCompleted(ConsumerRecord<String, byte[]> record) {
        try {
            InferenceResponse response = InferenceResponse.parseFrom(record.value());
            toClassification(response).ifPresentOrElse(
                    classification -> classifications.apply(
                            UUID.fromString(response.getTrackId()), classification),
                    () -> log.debug("Inference for track {} carries no usable classification",
                            response.getTrackId()));
        } catch (InvalidProtocolBufferException ex) {
            log.warn("Skipping unparseable inference result at offset {}", record.offset(), ex);
        } catch (IllegalArgumentException ex) {
            // A track id that is not a UUID: the message is for something this service does not own.
            log.warn("Skipping inference result with an unusable track id at offset {}",
                    record.offset(), ex);
        }
    }

    /**
     * @return the classification, or empty when the result carries nothing worth recording — a
     *     failure status, or a model that suppressed every prediction below the threshold. Recording
     *     a label-less classification would imply the track had been judged when it had not.
     */
    private static java.util.Optional<TrackClassification> toClassification(InferenceResponse response) {
        if (!isUsable(response.getStatus()) || response.getPredictionsCount() == 0) {
            return java.util.Optional.empty();
        }
        Prediction top = response.getPredictionsList().stream()
                .min(Comparator.comparingInt(Prediction::getRank))
                .orElseThrow();
        return java.util.Optional.of(new TrackClassification(
                top.getLabel(), top.getLabelClass(), top.getConfidence(),
                response.getModelId(), response.getModelVersion(),
                response.getDevice().name(), Instant.now()));
    }

    private static boolean isUsable(InferenceStatus status) {
        return status == InferenceStatus.STATUS_OK
                || status == InferenceStatus.STATUS_LOW_CONFIDENCE
                || status == InferenceStatus.STATUS_DEGRADED;
    }
}
