package pk.nust.cuas.orchestrator.application.port;

import pk.nust.cuas.orchestrator.domain.model.DegradationReason;
import pk.nust.cuas.orchestrator.domain.model.ServiceId;

/**
 * Notified whenever a call falls back to the mock (ARCHITECTURE.md sections 5.6, 6.5).
 *
 * <p>An output port so the dispatcher stays free of Kafka. Section 6.5 requires the degraded state to
 * reach the operator — "the operator always knows what produced the number" — and
 * {@code ai.service.degraded.v1} is how; but publishing is an adapter's job, and M6.3 ships the
 * decision while M6.4 ships the topic.
 *
 * <p>Implementations must not throw. A failure to <em>announce</em> degradation cannot be allowed to
 * fail the inference that was successfully degraded — that would turn a handled outage into an
 * unhandled one, which is the precise opposite of what this port exists for.
 */
public interface DegradationListener {

    /**
     * @param service the service that could not be reached
     * @param reason  what went wrong
     */
    void onDegraded(ServiceId service, DegradationReason reason);

    /** A listener that does nothing — the default until M6.4 wires the topic. */
    static DegradationListener noop() {
        return (service, reason) -> {
            // Recorded by the dispatcher's own logging and metrics; nothing further to do yet.
        };
    }
}
