package pk.nust.cuas.track.infrastructure.client;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.grpc.ManagedChannel;
import io.grpc.StatusRuntimeException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pk.nust.cuas.commons.correlation.CorrelationId;
import pk.nust.cuas.commons.id.Uuidv7Generator;
import pk.nust.cuas.detection.v1.DetectionRecord;
import pk.nust.cuas.inference.v1.InferenceOptions;
import pk.nust.cuas.inference.v1.InferenceRequest;
import pk.nust.cuas.inference.v1.InferenceResponse;
import pk.nust.cuas.inference.v1.InferenceServiceGrpc;
import pk.nust.cuas.inference.v1.InferenceStatus;
import pk.nust.cuas.inference.v1.Prediction;
import pk.nust.cuas.track.application.port.TrackClassifier;
import pk.nust.cuas.track.domain.model.Track;
import pk.nust.cuas.track.domain.model.TrackClassification;

/**
 * The circuit-broken {@code Infer} call to the AI orchestrator (ARCHITECTURE.md sections 4.1.5,
 * 4.3, 6.5).
 *
 * <p>The single synchronous dependency the correlation pipeline has, and the reason it is guarded so
 * carefully: it sits <b>inside the detection-processing transaction</b>. Every millisecond spent here
 * is a millisecond the consumer is not reading the next detection, so §5.8's 80 ms correlation budget
 * has to hold even when the orchestrator does not.
 *
 * <p><b>It never throws and never blocks indefinitely.</b> Three layers, all mattering:
 * <ul>
 *   <li>a gRPC <b>deadline</b>, so a slow orchestrator cannot hold the consumer;</li>
 *   <li>a <b>circuit breaker</b>, so a <em>failing</em> orchestrator costs one fast rejection per
 *       detection rather than a full deadline each time — the difference between a degraded tracker
 *       and a stalled one;</li>
 *   <li>a catch-all returning {@link Optional#empty()}, because §5.6 is explicit that the track is
 *       never dropped. An unclassified track is still a track, and classification is an enrichment.</li>
 * </ul>
 *
 * <p>Note the orchestrator already falls back to its mock internally (§6.5), so a breaker opening
 * here means the orchestrator itself is unreachable, not that a model is misbehaving. Those are
 * genuinely different failures and this is the one track-correlation can do nothing about.
 */
public class AiOrchestratorGrpcClient implements TrackClassifier {

    private static final Logger log = LoggerFactory.getLogger(AiOrchestratorGrpcClient.class);

    private final ManagedChannel channel;
    private final CircuitBreaker breaker;
    private final Uuidv7Generator idGenerator;
    private final Duration timeout;

    public AiOrchestratorGrpcClient(ManagedChannel channel, CircuitBreaker breaker,
                                    Uuidv7Generator idGenerator, Duration timeout) {
        this.channel = channel;
        this.breaker = breaker;
        this.idGenerator = idGenerator;
        this.timeout = timeout;
    }

    @Override
    public Optional<TrackClassification> classify(Track track, DetectionRecord detection) {
        try {
            InferenceResponse response = breaker.executeSupplier(() -> call(track, detection));
            return toClassification(response);
        } catch (CallNotPermittedException ex) {
            // Open breaker. Deliberately at debug: under a sustained outage this fires once per
            // detection, and a warning per detection would bury every other signal in the log.
            log.debug("AI orchestrator circuit is open; track {} stays unclassified", track.id());
            return Optional.empty();
        } catch (RuntimeException ex) {
            log.warn("Could not classify track {}: {}", track.id(), ex.toString());
            return Optional.empty();
        }
    }

    private InferenceResponse call(Track track, DetectionRecord detection) {
        InferenceRequest request = InferenceRequest.newBuilder()
                .setRequestId(idGenerator.generate(Instant.now()).toString())
                .setTrackId(track.id().toString())
                .setCorrelationId(CorrelationId.current().orElse(""))
                .addDetections(detection)
                .setOptions(InferenceOptions.newBuilder()
                        .setTimeoutMs((int) timeout.toMillis()))
                .build();
        try {
            return InferenceServiceGrpc.newBlockingStub(channel)
                    .withDeadlineAfter(timeout.toMillis(), TimeUnit.MILLISECONDS)
                    .infer(request);
        } catch (StatusRuntimeException ex) {
            // Rethrown unwrapped so the breaker records it; classify() turns it into an empty result.
            throw new IllegalStateException("inference call failed: " + ex.getStatus(), ex);
        }
    }

    /**
     * Maps a response to a classification, or empty when there is nothing worth recording.
     *
     * <p>A response with no predictions — {@code INSUFFICIENT_DATA}, or a model that suppressed
     * everything below the confidence threshold — is *not* an error, but it is also not a
     * classification. Recording a label-less classification would put an empty badge on the
     * operator's screen and imply the track had been judged when it had not.
     */
    private static Optional<TrackClassification> toClassification(InferenceResponse response) {
        if (!isUsable(response.getStatus()) || response.getPredictionsCount() == 0) {
            return Optional.empty();
        }
        Prediction top = response.getPredictionsList().stream()
                .min(java.util.Comparator.comparingInt(Prediction::getRank))
                .orElseThrow();
        return Optional.of(new TrackClassification(
                top.getLabel(),
                top.getLabelClass(),
                top.getConfidence(),
                response.getModelId(),
                response.getModelVersion(),
                response.getDevice().name(),
                Instant.now()));
    }

    private static boolean isUsable(InferenceStatus status) {
        return status == InferenceStatus.STATUS_OK
                || status == InferenceStatus.STATUS_LOW_CONFIDENCE
                || status == InferenceStatus.STATUS_DEGRADED;
    }
}
