package pk.nust.cuas.orchestrator.infrastructure.resilience;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pk.nust.cuas.inference.v1.InferenceRequest;
import pk.nust.cuas.inference.v1.InferenceResponse;
import pk.nust.cuas.orchestrator.application.port.DegradationListener;
import pk.nust.cuas.orchestrator.application.port.InferenceProvider;
import pk.nust.cuas.orchestrator.common.InferenceStatusMapper;
import pk.nust.cuas.orchestrator.domain.model.AiServiceDescriptor;
import pk.nust.cuas.orchestrator.domain.model.DegradationReason;
import pk.nust.cuas.orchestrator.domain.model.InferenceResult;

/**
 * Calls a provider under a timeout, a circuit breaker and a bulkhead, falling back to the mock
 * (ARCHITECTURE.md sections 4.1.6, 5.6, 6.5).
 *
 * <p><b>The track is never dropped, only degraded.</b> Section 5.6 states the governing principle: a
 * drone detected but scored late is a degraded outcome; a drone never scored is an undetected one. So
 * no path here propagates a provider failure to the caller — every failure resolves to a mock
 * response tagged with why.
 *
 * <p>The three protections answer different questions and are layered in the order section 6.5's
 * sequence shows:
 * <ol>
 *   <li><b>Bulkhead</b> — is this service already at its concurrency limit? Rejecting here costs
 *       nothing, and is what stops a burst from overwhelming a GPU (section 4.1.6).</li>
 *   <li><b>Circuit breaker</b> — has this service been failing? An open breaker rejects without
 *       attempting, so a service that is down is left alone to recover rather than being hammered.</li>
 *   <li><b>Time limiter</b> — did this particular call answer inside its budget?</li>
 * </ol>
 *
 * <p><b>Only transport failures reach the breaker.</b> A provider returning
 * {@code STATUS_MODEL_ERROR} or {@code STATUS_LOW_CONFIDENCE} has answered — that is the model's
 * verdict, not evidence the service is unreachable — and it is recorded as a success against the
 * breaker while being passed through unchanged to the caller. Counting a model's honest uncertainty
 * as a failure would take a healthy service out of rotation for telling the truth, and would make the
 * breaker fire hardest exactly when the airspace is most ambiguous.
 *
 * <p>Each service gets its own breaker and bulkhead, keyed by service id, so one failing service
 * cannot open another's breaker or consume its permits.
 */
public class ResilientInferenceDispatcher {

    private static final Logger log = LoggerFactory.getLogger(ResilientInferenceDispatcher.class);

    private final CircuitBreakerRegistry breakers;
    private final BulkheadRegistry bulkheads;
    private final InferenceProvider mock;
    private final DegradationListener degradationListener;
    private final ExecutorService timeoutExecutor;

    public ResilientInferenceDispatcher(CircuitBreakerRegistry breakers,
                                        BulkheadRegistry bulkheads,
                                        InferenceProvider mock,
                                        DegradationListener degradationListener,
                                        ExecutorService timeoutExecutor) {
        this.breakers = breakers;
        this.bulkheads = bulkheads;
        this.mock = mock;
        this.degradationListener = degradationListener;
        this.timeoutExecutor = timeoutExecutor;
    }

    /**
     * Runs one inference against a service, degrading rather than failing.
     *
     * @param service  the service to call
     * @param provider the provider configured for it
     * @param request  the request
     * @return the result, tagged with whether the primary or the fallback produced it
     */
    public InferenceResult dispatch(AiServiceDescriptor service, InferenceProvider provider,
                                    InferenceRequest request) {
        String key = service.id().value();
        Bulkhead bulkhead = bulkheads.bulkhead(key);
        CircuitBreaker breaker = breakers.circuitBreaker(key);

        try {
            return InferenceResult.primary(service.id(), callGuarded(service, provider, request,
                    bulkhead, breaker));
        } catch (BulkheadFullException ex) {
            // Healthy but saturated. Section 5.6: overflow degrades rather than queues unboundedly.
            return degrade(service, request, DegradationReason.BULKHEAD_FULL);
        } catch (CallNotPermittedException ex) {
            // Open breaker: rejected fast, without troubling a service that is already struggling.
            return degrade(service, request, DegradationReason.CIRCUIT_OPEN);
        } catch (InferenceTimeoutException ex) {
            return degrade(service, request, DegradationReason.TIMEOUT);
        } catch (RuntimeException ex) {
            log.warn("Inference transport failure on {}: {}", key, ex.toString());
            return degrade(service, request, DegradationReason.TRANSPORT_ERROR);
        }
    }

    /**
     * Bulkhead → breaker → time limiter, innermost last.
     *
     * <p>The breaker sits <em>inside</em> the bulkhead deliberately: a bulkhead rejection means the
     * service is busy, not broken, and must not count towards opening its breaker. Reversing them
     * would let a traffic spike open the breaker of a perfectly healthy service.
     */
    private InferenceResponse callGuarded(AiServiceDescriptor service, InferenceProvider provider,
                                          InferenceRequest request, Bulkhead bulkhead,
                                          CircuitBreaker breaker) {
        return Bulkhead.decorateSupplier(bulkhead,
                CircuitBreaker.decorateSupplier(breaker,
                        () -> callWithTimeout(service, provider, request))).get();
    }

    /**
     * Enforces the per-service deadline (section 4.1.6).
     *
     * <p>Run on a separate thread so a provider that blocks indefinitely cannot hold the caller: a
     * deadline that only fires when the callee cooperates is not a deadline. The future is cancelled
     * with interruption so a well-behaved provider stops work rather than continuing to burn a GPU on
     * a result nobody will read.
     */
    private InferenceResponse callWithTimeout(AiServiceDescriptor service, InferenceProvider provider,
                                              InferenceRequest request) {
        TimeLimiter limiter = TimeLimiter.of(TimeLimiterConfig.custom()
                .timeoutDuration(service.timeout())
                .cancelRunningFuture(true)
                .build());
        try {
            return limiter.executeFutureSupplier(
                    () -> CompletableFuture.supplyAsync(
                            () -> requireTransportSuccess(service, provider.infer(service, request)),
                            timeoutExecutor));
        } catch (TimeoutException ex) {
            // Wrapped so dispatch() can tell a deadline from any other transport fault; both are
            // transport failures the breaker counts, but they read differently to an operator.
            throw new InferenceTimeoutException(service.id().value(), ex);
        } catch (RuntimeException ex) {
            throw unwrap(ex);
        } catch (Exception ex) {
            throw new IllegalStateException("inference failed on " + service.id(), ex);
        }
    }

    /**
     * Turns a response that <em>reports</em> a transport failure into a thrown one, so the breaker
     * counts it.
     *
     * <p>A provider is contractually allowed to report {@code STATUS_TIMEOUT} rather than throw — the
     * mock does exactly that under injected failure, and a gRPC client may surface a server-side
     * deadline the same way. Without this, an injected timeout would be recorded as a *success*
     * against the breaker and it could never open, which is precisely the behaviour M6 is supposed to
     * demonstrate.
     *
     * <p>Only transport statuses are lifted. {@code STATUS_MODEL_ERROR},
     * {@code STATUS_LOW_CONFIDENCE} and {@code STATUS_INSUFFICIENT_DATA} pass through untouched: the
     * model answered, and its answer is not evidence the service is unreachable.
     */
    private static InferenceResponse requireTransportSuccess(AiServiceDescriptor service,
                                                             InferenceResponse response) {
        if (InferenceStatusMapper.toDomain(response.getStatus()).isTransportFailure()) {
            throw new InferenceTimeoutException(service.id().value(), null);
        }
        return response;
    }

    /** Falls back to the mock and announces the degradation. */
    private InferenceResult degrade(AiServiceDescriptor service, InferenceRequest request,
                                    DegradationReason reason) {
        log.warn("Service {} degraded ({}), falling back to the mock provider",
                service.id(), reason);
        InferenceResponse response = mock.infer(service, request);
        notifyDegraded(service, reason);
        return InferenceResult.fallback(service.id(), response, reason);
    }

    /**
     * A listener failure must never fail the inference it is describing — that would turn a handled
     * outage into an unhandled one.
     */
    private void notifyDegraded(AiServiceDescriptor service, DegradationReason reason) {
        try {
            degradationListener.onDegraded(service.id(), reason);
        } catch (RuntimeException ex) {
            log.warn("Could not announce degradation of {}", service.id(), ex);
        }
    }

    /**
     * {@code CompletableFuture} wraps a provider's exception in a {@link CompletionException}; the
     * breaker and the {@code catch} clauses in {@link #dispatch} should see the real one, or a
     * provider's own {@code InferenceTimeoutException} would be misreported as a generic transport
     * error.
     */
    private static RuntimeException unwrap(RuntimeException ex) {
        if (ex instanceof CompletionException && ex.getCause() instanceof RuntimeException runtime) {
            return runtime;
        }
        return ex;
    }

    /** A deadline was exceeded. Distinct so the degradation reason can say so. */
    static final class InferenceTimeoutException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        InferenceTimeoutException(String serviceId, Throwable cause) {
            super("inference exceeded its deadline on " + serviceId, cause);
        }
    }
}
