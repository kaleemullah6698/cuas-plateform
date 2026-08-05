package pk.nust.cuas.orchestrator.infrastructure.resilience;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import pk.nust.cuas.inference.v1.ComputeDevice;
import pk.nust.cuas.inference.v1.InferenceRequest;
import pk.nust.cuas.inference.v1.InferenceResponse;
import pk.nust.cuas.inference.v1.InferenceStatus;
import pk.nust.cuas.inference.v1.ModelInfo;
import pk.nust.cuas.orchestrator.application.port.DegradationListener;
import pk.nust.cuas.orchestrator.application.port.InferenceProvider;
import pk.nust.cuas.orchestrator.domain.model.AiServiceDescriptor;
import pk.nust.cuas.orchestrator.domain.model.DegradationReason;
import pk.nust.cuas.orchestrator.domain.model.InferenceResult;
import pk.nust.cuas.orchestrator.domain.model.Modality;
import pk.nust.cuas.orchestrator.domain.model.ProviderMode;
import pk.nust.cuas.orchestrator.domain.model.ServiceId;
import pk.nust.cuas.orchestrator.domain.service.FailureInjector;
import pk.nust.cuas.orchestrator.domain.service.LatencyModel;
import pk.nust.cuas.orchestrator.domain.service.ScenarioLibrary;
import pk.nust.cuas.orchestrator.infrastructure.provider.MockInferenceProvider;

class ResilientInferenceDispatcherTest {

    private static final String SERVICE = "some-service";

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final MockInferenceProvider mock = new MockInferenceProvider(
            new ScenarioLibrary(), new LatencyModel(0.35d), FailureInjector.disabled(), false);
    private final List<DegradationReason> announced = new java.util.concurrent.CopyOnWriteArrayList<>();

    @AfterEach
    void shutdown() {
        executor.shutdownNow();
    }

    private static AiServiceDescriptor service(Duration timeout, int maxConcurrent) {
        return AiServiceDescriptor.of(new ServiceId(SERVICE), Set.of(Modality.RF), ProviderMode.GRPC,
                Optional.of("host:9001"), timeout, Optional.empty(), maxConcurrent);
    }

    private static AiServiceDescriptor service() {
        return service(Duration.ofMillis(200), 32);
    }

    private static InferenceRequest request() {
        return InferenceRequest.newBuilder()
                .setRequestId(UUID.randomUUID().toString())
                .setTrackId("track-" + UUID.randomUUID())
                .build();
    }

    private ResilientInferenceDispatcher dispatcher(CircuitBreakerRegistry breakers,
                                                    BulkheadRegistry bulkheads) {
        DegradationListener listener = (id, reason) -> announced.add(reason);
        return new ResilientInferenceDispatcher(breakers, bulkheads, mock, listener, executor);
    }

    private ResilientInferenceDispatcher dispatcher() {
        return dispatcher(CircuitBreakerRegistry.ofDefaults(), BulkheadRegistry.ofDefaults());
    }

    /** A breaker that opens after 4 failures in a window of 4 — quick enough to test. */
    private static CircuitBreakerRegistry fastBreakers() {
        return CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
                .failureRateThreshold(50.0f)
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(4)
                .minimumNumberOfCalls(4)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .build());
    }

    /** A provider that behaves however the test wants. */
    private record StubProvider(java.util.function.Supplier<InferenceResponse> behaviour)
            implements InferenceProvider {

        @Override
        public InferenceResponse infer(AiServiceDescriptor service, InferenceRequest request) {
            return behaviour.get();
        }

        @Override
        public ModelInfo modelInfo(AiServiceDescriptor service) {
            return ModelInfo.getDefaultInstance();
        }

        @Override
        public boolean isHealthy(AiServiceDescriptor service) {
            return true;
        }

        @Override
        public ProviderMode mode() {
            return ProviderMode.GRPC;
        }
    }

    private static InferenceResponse ok() {
        return InferenceResponse.newBuilder()
                .setStatus(InferenceStatus.STATUS_OK)
                .setModelVersion("1.3.0")
                .setDevice(ComputeDevice.DEVICE_CPU)
                .build();
    }

    @Test
    void aHealthyProvidersAnswerIsReturnedUnchanged() {
        InferenceResult result = dispatcher().dispatch(service(), new StubProvider(() -> ok()), request());

        assertThat(result.isDegraded()).isFalse();
        assertThat(result.reason()).isEmpty();
        assertThat(result.response().getModelVersion()).isEqualTo("1.3.0");
    }

    @Test
    void aTransportFailureFallsBackToTheMockRatherThanPropagating() {
        // Section 5.6's governing principle: the track is never dropped, only degraded.
        InferenceResult result = dispatcher().dispatch(service(),
                new StubProvider(() -> {
                    throw new IllegalStateException("connection refused");
                }), request());

        assertThat(result.isDegraded()).isTrue();
        assertThat(result.reason()).contains(DegradationReason.TRANSPORT_ERROR);
        assertThat(result.response().getDevice()).isEqualTo(ComputeDevice.DEVICE_MOCK);
        assertThat(announced).containsExactly(DegradationReason.TRANSPORT_ERROR);
    }

    @Test
    void aSlowProviderIsCutOffAtItsDeadlineAndDegrades() {
        InferenceResult result = dispatcher().dispatch(service(Duration.ofMillis(50), 32),
                new StubProvider(() -> {
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(ex);
                    }
                    return ok();
                }), request());

        assertThat(result.reason()).contains(DegradationReason.TIMEOUT);
        assertThat(result.response().getDevice()).isEqualTo(ComputeDevice.DEVICE_MOCK);
    }

    @Test
    void aModelErrorIsNotATransportFailureAndIsPassedThrough() {
        // The model answered. That is its verdict, not evidence the service is unreachable —
        // counting it would take a healthy service out of rotation for telling the truth.
        InferenceResponse modelError = InferenceResponse.newBuilder()
                .setStatus(InferenceStatus.STATUS_MODEL_ERROR)
                .setModelVersion("1.3.0")
                .build();

        InferenceResult result =
                dispatcher().dispatch(service(), new StubProvider(() -> modelError), request());

        assertThat(result.isDegraded()).as("not a fallback").isFalse();
        assertThat(result.response().getStatus()).isEqualTo(InferenceStatus.STATUS_MODEL_ERROR);
        assertThat(announced).isEmpty();
    }

    @Test
    void aLowConfidenceAnswerIsNotATransportFailure() {
        InferenceResponse lowConfidence = InferenceResponse.newBuilder()
                .setStatus(InferenceStatus.STATUS_LOW_CONFIDENCE)
                .build();

        InferenceResult result =
                dispatcher().dispatch(service(), new StubProvider(() -> lowConfidence), request());

        assertThat(result.isDegraded()).isFalse();
        assertThat(result.response().getStatus()).isEqualTo(InferenceStatus.STATUS_LOW_CONFIDENCE);
    }

    @Test
    void manyModelErrorsNeverOpenTheBreaker() {
        // The consequence of the distinction above, at scale: an ambiguous airspace must not take
        // the service offline.
        CircuitBreakerRegistry breakers = fastBreakers();
        InferenceResponse modelError = InferenceResponse.newBuilder()
                .setStatus(InferenceStatus.STATUS_MODEL_ERROR).build();
        ResilientInferenceDispatcher dispatcher = dispatcher(breakers, BulkheadRegistry.ofDefaults());

        for (int i = 0; i < 30; i++) {
            dispatcher.dispatch(service(), new StubProvider(() -> modelError), request());
        }

        assertThat(breakers.circuitBreaker(SERVICE).getState())
                .isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(announced).isEmpty();
    }

    @Test
    void repeatedTransportFailuresOpenTheBreakerAndSubsequentCallsRejectFast() {
        CircuitBreakerRegistry breakers = fastBreakers();
        ResilientInferenceDispatcher dispatcher = dispatcher(breakers, BulkheadRegistry.ofDefaults());
        AtomicInteger attempts = new AtomicInteger();
        StubProvider failing = new StubProvider(() -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("connection refused");
        });

        for (int i = 0; i < 4; i++) {
            dispatcher.dispatch(service(), failing, request());
        }
        assertThat(breakers.circuitBreaker(SERVICE).getState()).isEqualTo(CircuitBreaker.State.OPEN);

        int attemptsBefore = attempts.get();
        InferenceResult afterOpen = dispatcher.dispatch(service(), failing, request());

        assertThat(afterOpen.reason()).contains(DegradationReason.CIRCUIT_OPEN);
        assertThat(attempts.get())
                .as("an open breaker rejects without troubling a service that is already struggling")
                .isEqualTo(attemptsBefore);
        assertThat(afterOpen.response().getDevice()).isEqualTo(ComputeDevice.DEVICE_MOCK);
    }

    @Test
    void aProviderReportingATimeoutStatusAlsoOpensTheBreaker() {
        // The mock reports an injected timeout as a status rather than throwing. Without lifting it,
        // it would count as a success and the breaker could never open under injected failure —
        // which is exactly the behaviour M6 is meant to demonstrate.
        CircuitBreakerRegistry breakers = fastBreakers();
        ResilientInferenceDispatcher dispatcher = dispatcher(breakers, BulkheadRegistry.ofDefaults());
        InferenceResponse timedOut = InferenceResponse.newBuilder()
                .setStatus(InferenceStatus.STATUS_TIMEOUT).build();

        for (int i = 0; i < 4; i++) {
            dispatcher.dispatch(service(), new StubProvider(() -> timedOut), request());
        }

        assertThat(breakers.circuitBreaker(SERVICE).getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(announced).allMatch(r -> r == DegradationReason.TIMEOUT);
    }

    @Test
    void aSaturatedBulkheadDegradesImmediatelyWithoutQueueing() throws Exception {
        BulkheadRegistry bulkheads = BulkheadRegistry.of(BulkheadConfig.custom()
                .maxConcurrentCalls(1).maxWaitDuration(Duration.ZERO).build());
        ResilientInferenceDispatcher dispatcher =
                dispatcher(CircuitBreakerRegistry.ofDefaults(), bulkheads);

        CountDownLatch inFlight = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        StubProvider blocking = new StubProvider(() -> {
            inFlight.countDown();
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            return ok();
        });

        AtomicReference<InferenceResult> second = new AtomicReference<>();
        Thread holder = Thread.startVirtualThread(() ->
                dispatcher.dispatch(service(Duration.ofSeconds(10), 1), blocking, request()));
        assertThat(inFlight.await(5, TimeUnit.SECONDS)).isTrue();

        second.set(dispatcher.dispatch(service(Duration.ofSeconds(10), 1), blocking, request()));

        release.countDown();
        holder.join(5000);

        assertThat(second.get().reason())
                .as("saturated, not broken — and degraded rather than queued behind a deadline")
                .contains(DegradationReason.BULKHEAD_FULL);
        assertThat(second.get().response().getDevice()).isEqualTo(ComputeDevice.DEVICE_MOCK);
    }

    @Test
    void aBulkheadRejectionDoesNotOpenTheBreaker() {
        // A traffic spike must not take a perfectly healthy service offline. This is why the breaker
        // sits inside the bulkhead rather than outside it.
        CircuitBreakerRegistry breakers = fastBreakers();
        BulkheadRegistry bulkheads = BulkheadRegistry.of(BulkheadConfig.custom()
                .maxConcurrentCalls(1).maxWaitDuration(Duration.ZERO).build());
        ResilientInferenceDispatcher dispatcher = dispatcher(breakers, bulkheads);

        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch inFlight = new CountDownLatch(1);
        StubProvider blocking = new StubProvider(() -> {
            inFlight.countDown();
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            return ok();
        });

        Thread holder = Thread.startVirtualThread(() ->
                dispatcher.dispatch(service(Duration.ofSeconds(10), 1), blocking, request()));
        try {
            assertThat(inFlight.await(5, TimeUnit.SECONDS)).isTrue();
            for (int i = 0; i < 10; i++) {
                dispatcher.dispatch(service(Duration.ofSeconds(10), 1), blocking, request());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } finally {
            release.countDown();
        }

        assertThat(breakers.circuitBreaker(SERVICE).getState())
                .isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void aListenerThatThrowsDoesNotFailTheInferenceItDescribes() {
        // Failing to announce a handled outage must not turn it into an unhandled one.
        ResilientInferenceDispatcher dispatcher = new ResilientInferenceDispatcher(
                CircuitBreakerRegistry.ofDefaults(), BulkheadRegistry.ofDefaults(), mock,
                (id, reason) -> {
                    throw new IllegalStateException("kafka is down");
                }, executor);

        InferenceResult result = dispatcher.dispatch(service(), new StubProvider(() -> {
            throw new IllegalStateException("connection refused");
        }), request());

        assertThat(result.isDegraded()).isTrue();
        assertThat(result.response().getDevice()).isEqualTo(ComputeDevice.DEVICE_MOCK);
    }

    @Test
    void eachServiceGetsItsOwnBreaker() {
        // One failing service must not open another's breaker.
        CircuitBreakerRegistry breakers = fastBreakers();
        ResilientInferenceDispatcher dispatcher = dispatcher(breakers, BulkheadRegistry.ofDefaults());
        AiServiceDescriptor other = AiServiceDescriptor.of(new ServiceId("another-service"),
                Set.of(Modality.RF), ProviderMode.GRPC, Optional.of("h:1"),
                Duration.ofMillis(200), Optional.empty(), 32);

        for (int i = 0; i < 4; i++) {
            dispatcher.dispatch(service(), new StubProvider(() -> {
                throw new IllegalStateException("down");
            }), request());
        }

        assertThat(breakers.circuitBreaker(SERVICE).getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(breakers.circuitBreaker(other.id().value()).getState())
                .isEqualTo(CircuitBreaker.State.CLOSED);
    }
}
