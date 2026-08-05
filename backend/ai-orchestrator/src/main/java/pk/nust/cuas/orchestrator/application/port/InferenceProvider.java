package pk.nust.cuas.orchestrator.application.port;

import pk.nust.cuas.inference.v1.InferenceRequest;
import pk.nust.cuas.inference.v1.InferenceResponse;
import pk.nust.cuas.inference.v1.ModelInfo;
import pk.nust.cuas.orchestrator.domain.model.AiServiceDescriptor;

/**
 * How the orchestrator reaches one AI service (ARCHITECTURE.md section 6.4).
 *
 * <p><b>The single most important abstraction in the plan.</b> Everything downstream of it — routing,
 * timeouts, breakers, bulkheads, caching, canary, the published events — is written against this
 * interface and never against a particular model. Two implementations satisfy it: the mock provider,
 * and a gRPC client for a real service. Moving a trained model into production is therefore a
 * configuration change (section 6.4), not a code change, and that is what lets Track A ship complete
 * before Track B exists.
 *
 * <p><b>Deliberately expressed in the generated Protobuf types</b>, not in orchestrator-local records.
 * Section 6.2 <em>is</em> the contract, and section 6.4 requires the mock to produce
 * schema-identical output so conformance is compile-enforced rather than asserted. A private
 * translation layer here would let the mock and a real service drift apart in exactly the way the
 * shared conformance suite exists to prevent. This is why the port lives in {@code application/}
 * rather than {@code domain/}: the domain stays free of generated types (CLAUDE.md section 2), while
 * the boundary speaks the wire contract.
 *
 * <p>Implementations must not throw for an <em>inference</em> failure — a model that could not
 * produce a result returns a response whose {@code status} says so ({@code STATUS_MODEL_ERROR},
 * {@code STATUS_LOW_CONFIDENCE}, {@code STATUS_INSUFFICIENT_DATA}). Exceptions are reserved for a
 * <em>transport</em> failure, which is what the circuit breaker is measuring. Conflating the two
 * would open a breaker because a model honestly reported low confidence.
 */
public interface InferenceProvider {

    /**
     * Runs one inference.
     *
     * @param service the service being called, so a single provider implementation can serve many
     *                registered services — the mock serves all of them, and one gRPC provider serves
     *                any endpoint. Without this the orchestrator would need a bean per model, which
     *                is the code change section 6.1 forbids.
     * @param request the request, already carrying its correlation id and timeout budget
     * @return the response; never null
     * @throws RuntimeException on transport failure only
     */
    InferenceResponse infer(AiServiceDescriptor service, InferenceRequest request);

    /**
     * @param service the service to describe
     * @return its model metadata — id, version, SHA, labels, latency budget (section 6.6)
     */
    ModelInfo modelInfo(AiServiceDescriptor service);

    /**
     * @param service the service to probe
     * @return whether it is currently serving; drives {@code GET /ai/services} and the fallback
     *     decision
     */
    boolean isHealthy(AiServiceDescriptor service);

    /** @return the mode this provider implements, so the registry can bind services to it */
    pk.nust.cuas.orchestrator.domain.model.ProviderMode mode();
}
