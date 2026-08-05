package pk.nust.cuas.orchestrator.infrastructure.messaging.producer;

import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import pk.nust.cuas.inference.v1.InferenceRequest;
import pk.nust.cuas.inference.v1.InferenceResponse;
import pk.nust.cuas.orchestrator.application.port.DegradationListener;
import pk.nust.cuas.orchestrator.application.port.InferenceEventPublisher;
import pk.nust.cuas.orchestrator.common.InferenceStatusMapper;
import pk.nust.cuas.orchestrator.domain.model.DegradationReason;
import pk.nust.cuas.orchestrator.domain.model.InferenceResult;
import pk.nust.cuas.orchestrator.domain.model.ServiceId;
import pk.nust.cuas.orchestrator.infrastructure.messaging.InferenceTopics;

/**
 * Kafka adapter for the orchestrator's four output topics (ARCHITECTURE.md sections 4.1.6, 5.2).
 *
 * <p>Implements both {@link InferenceEventPublisher} and {@link DegradationListener}: a degradation
 * *is* one of the four events, and splitting it across two adapters would put two Kafka templates and
 * two error policies where one belongs.
 *
 * <p><b>Keyed by {@code trackId}</b> for the three inference topics, so one track's inference events
 * stay ordered on a single partition — a consumer must never see a completion before the request that
 * produced it. {@code ai.service.degraded.v1} is keyed by {@code serviceId} instead, because it is a
 * statement about a *service* over time, and an operator watching one service's health wants its
 * events in order.
 *
 * <p><b>Publication never fails the inference.</b> Sends are fire-and-forget with a logged failure.
 * This is the opposite of track-correlation's synchronous publish, and deliberately so: there, a lost
 * track event means a track nothing downstream knows about, so the transaction must roll back and
 * reprocess. Here the caller already has its answer in hand and is returning it synchronously to
 * track-correlation; failing the call because telemetry could not be written would convert a
 * successful inference into a failed one.
 */
@Component
public class InferenceEventKafkaPublisher implements InferenceEventPublisher, DegradationListener {

    private static final Logger log = LoggerFactory.getLogger(InferenceEventKafkaPublisher.class);
    private static final long SEND_TIMEOUT_SECONDS = 5;

    private final KafkaTemplate<String, byte[]> kafkaTemplate;

    public InferenceEventKafkaPublisher(KafkaTemplate<String, byte[]> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void publishRequested(ServiceId service, InferenceRequest request) {
        send(InferenceTopics.INFERENCE_REQUESTED, request.getTrackId(), request.toByteArray());
    }

    @Override
    public void publishOutcome(InferenceRequest request, InferenceResult result) {
        InferenceResponse response = result.response();
        boolean usable = InferenceStatusMapper.toDomain(response.getStatus()).isUsable();

        // Failures are kept off the completed topic so a consumer never has to distinguish
        // "scored zero" from "not scored".
        send(usable ? InferenceTopics.INFERENCE_COMPLETED : InferenceTopics.INFERENCE_FAILED,
                request.getTrackId(), response.toByteArray());

        // A degraded call emits both: downstream gets a usable result, and the operator gets the
        // caveat attached to it (section 6.5).
        result.reason().ifPresent(reason -> onDegraded(result.service(), reason));
    }

    @Override
    public void onDegraded(ServiceId service, DegradationReason reason) {
        // The payload is deliberately minimal and human-readable: no contract message is specified
        // for this topic (section 5.2 names it; no schema is given), and inventing one would be a
        // contract change without authority. Service and reason are what section 7.6's SIEM
        // forwarding and the section 2.4 degraded badge need.
        String payload = service.value() + ' ' + reason.name();
        send(InferenceTopics.SERVICE_DEGRADED, service.value(),
                payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private void send(String topic, String key, byte[] payload) {
        try {
            kafkaTemplate.send(topic, key, payload).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted publishing to {}", topic);
        } catch (Exception ex) {
            // See the class note: telemetry must not fail an inference the caller already holds.
            log.warn("Could not publish to {} (key {})", topic, key, ex);
        }
    }
}
