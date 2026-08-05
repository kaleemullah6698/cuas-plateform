package pk.nust.cuas.orchestrator.application.port;

import pk.nust.cuas.inference.v1.InferenceRequest;
import pk.nust.cuas.orchestrator.domain.model.InferenceResult;
import pk.nust.cuas.orchestrator.domain.model.ServiceId;

/**
 * Publishes the orchestrator's inference events (ARCHITECTURE.md sections 4.1.6, 5.2).
 *
 * <p>An output port, so the application layer never names Kafka. Four topics, and the split between
 * them is the point:
 *
 * <ul>
 *   <li>{@code ai.inference.requested.v1} — a call was dispatched. Reporting uses it to measure
 *       how much inference the system is actually asking for, which cannot be recovered from the
 *       completions alone once failures are excluded.</li>
 *   <li>{@code ai.inference.completed.v1} — a usable result. This is what threat-assessment and
 *       track-correlation consume, so it must carry <em>only</em> answers they can score.</li>
 *   <li>{@code ai.inference.failed.v1} — no usable result. Kept off the completed topic so a
 *       consumer never has to distinguish "scored zero" from "not scored"; alert-service watches
 *       this one because a model that stops answering is an operational event.</li>
 *   <li>{@code ai.service.degraded.v1} — a fallback produced the answer. Separate from
 *       {@code failed} because the inference *succeeded*; what degraded is the service. §6.5
 *       requires this to reach the operator so "the operator always knows what produced the
 *       number".</li>
 * </ul>
 *
 * <p>A degraded call therefore emits both {@code completed} and {@code degraded}: downstream gets a
 * usable result, and the operator gets the caveat attached to it.
 */
public interface InferenceEventPublisher {

    /**
     * @param service the service being asked
     * @param request the request as dispatched
     */
    void publishRequested(ServiceId service, InferenceRequest request);

    /**
     * Publishes the outcome: completed or failed, plus degraded when a fallback answered.
     *
     * @param request the originating request, for its correlation id and track id
     * @param result  what came back, and how
     */
    void publishOutcome(InferenceRequest request, InferenceResult result);
}
