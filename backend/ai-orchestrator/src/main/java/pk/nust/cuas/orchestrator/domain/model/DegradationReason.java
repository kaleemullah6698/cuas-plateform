package pk.nust.cuas.orchestrator.domain.model;

/**
 * Why a call fell back to the mock (ARCHITECTURE.md sections 5.6, 6.5).
 *
 * <p>Carried on {@code ai.service.degraded.v1} so an operator and a reviewer can tell *what kind* of
 * trouble a service is in. The three mean genuinely different things operationally: an open breaker
 * says the service has been failing for a while and is being left alone to recover; a rejected
 * bulkhead says the service is healthy but saturated; a timeout says this one call was too slow. The
 * remedies differ — wait, scale, or investigate — so collapsing them into "degraded" would throw away
 * the only signal that distinguishes them.
 */
public enum DegradationReason {

    /** The breaker was open, so the call was rejected without being attempted. */
    CIRCUIT_OPEN,

    /** The bulkhead had no permit: the service is healthy but at its concurrency limit. */
    BULKHEAD_FULL,

    /** The call was attempted and exceeded its deadline. */
    TIMEOUT,

    /** The call failed at the transport level for some other reason. */
    TRANSPORT_ERROR
}
