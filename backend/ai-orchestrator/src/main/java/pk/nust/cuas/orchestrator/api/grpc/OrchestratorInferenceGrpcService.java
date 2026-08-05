package pk.nust.cuas.orchestrator.api.grpc;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import pk.nust.cuas.commons.correlation.CorrelationId;
import pk.nust.cuas.inference.v1.InferenceRequest;
import pk.nust.cuas.inference.v1.InferenceResponse;
import pk.nust.cuas.inference.v1.InferenceServiceGrpc;
import pk.nust.cuas.inference.v1.InferenceStatus;
import pk.nust.cuas.orchestrator.application.InferenceOrchestrator;
import pk.nust.cuas.orchestrator.domain.model.InferenceResult;

/**
 * The orchestrator's own {@code Infer} endpoint — what track-correlation calls (ARCHITECTURE.md
 * sections 4.1.5, 4.1.6, 6.5).
 *
 * <p><b>The orchestrator implements the same {@code InferenceService} contract it consumes.</b> That
 * is deliberate and is what makes §6.5's sequence diagram literal: track-correlation issues
 * {@code Infer} and the orchestrator fans it out. Defining a second, orchestrator-specific RPC would
 * add a contract to maintain and would let the two drift; reusing §6.2 means the caller's stub, the
 * conformance suite and the wire format are already agreed.
 *
 * <p><b>One response, from many services.</b> A request may route to several services (§6.1), but the
 * caller asked one question about one track. The <em>most confident usable</em> answer is returned:
 * confidence is calibrated and comparable across services by construction (§6.2's
 * {@code ConfidenceReport}), so "most confident" is a meaningful ordering rather than an arbitrary
 * pick. Every individual result still reaches Kafka, so nothing is lost — threat-assessment sees them
 * all and can weigh cross-sensor agreement itself (§6.3.3).
 *
 * <p>When nothing routes, or every service failed, the response carries
 * {@code STATUS_INSUFFICIENT_DATA} rather than an error status: no model was in a position to judge,
 * and that is a fact about the input rather than a fault. Track-correlation treats it as "no
 * classification yet" and carries on — §5.6's rule that the track is never dropped.
 */
@Component
public class OrchestratorInferenceGrpcService extends InferenceServiceGrpc.InferenceServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(OrchestratorInferenceGrpcService.class);

    private final InferenceOrchestrator orchestrator;

    public OrchestratorInferenceGrpcService(InferenceOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @Override
    public void infer(InferenceRequest request, StreamObserver<InferenceResponse> responses) {
        // The caller's correlation id spans the whole pipeline (section 5.3); adopting it here is
        // what lets one detection be traced from sensor to alert.
        CorrelationId.set(request.getCorrelationId().isBlank()
                ? CorrelationId.generate() : request.getCorrelationId());
        try {
            List<InferenceResult> results = orchestrator.infer(request);
            responses.onNext(bestOf(request, results));
            responses.onCompleted();
        } catch (RuntimeException ex) {
            // The orchestrator degrades rather than throws, so reaching here means something
            // unforeseen. The caller is circuit-broken and will fall back, so an honest error is
            // better than a fabricated result.
            log.error("Inference failed for track {}", request.getTrackId(), ex);
            responses.onError(Status.INTERNAL
                    .withDescription("inference failed").asRuntimeException());
        } finally {
            CorrelationId.clear();
        }
    }

    /**
     * Picks the answer to return.
     *
     * @return the most confident usable result, or an {@code INSUFFICIENT_DATA} response when none is
     */
    private static InferenceResponse bestOf(InferenceRequest request, List<InferenceResult> results) {
        return results.stream()
                .map(InferenceResult::response)
                .filter(response -> usable(response.getStatus()))
                .max(java.util.Comparator.comparingDouble(r -> r.getConfidence().getCalibratedScore()))
                .orElseGet(() -> nothingToJudge(request));
    }

    private static boolean usable(InferenceStatus status) {
        return status == InferenceStatus.STATUS_OK
                || status == InferenceStatus.STATUS_LOW_CONFIDENCE
                || status == InferenceStatus.STATUS_DEGRADED;
    }

    private static InferenceResponse nothingToJudge(InferenceRequest request) {
        return InferenceResponse.newBuilder()
                .setRequestId(request.getRequestId())
                .setTrackId(request.getTrackId())
                .setStatus(InferenceStatus.STATUS_INSUFFICIENT_DATA)
                .setErrorMessage("no AI service produced a usable result for this track")
                .build();
    }
}
