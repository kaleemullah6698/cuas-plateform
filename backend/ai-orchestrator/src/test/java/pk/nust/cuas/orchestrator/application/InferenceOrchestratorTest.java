package pk.nust.cuas.orchestrator.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pk.nust.cuas.detection.v1.DetectionRecord;
import pk.nust.cuas.detection.v1.SensorModality;
import pk.nust.cuas.inference.v1.ComputeDevice;
import pk.nust.cuas.inference.v1.InferenceRequest;
import pk.nust.cuas.inference.v1.InferenceResponse;
import pk.nust.cuas.inference.v1.InferenceStatus;
import pk.nust.cuas.orchestrator.application.port.InferenceCache;
import pk.nust.cuas.orchestrator.application.port.InferenceEventPublisher;
import pk.nust.cuas.orchestrator.domain.model.AiServiceDescriptor;
import pk.nust.cuas.orchestrator.domain.model.InferenceResult;
import pk.nust.cuas.orchestrator.domain.model.Modality;
import pk.nust.cuas.orchestrator.domain.model.ProviderMode;
import pk.nust.cuas.orchestrator.domain.model.ServiceId;
import pk.nust.cuas.orchestrator.domain.service.AiServiceRegistry;
import pk.nust.cuas.orchestrator.domain.service.CanaryPolicy;
import pk.nust.cuas.orchestrator.domain.service.FailureInjector;
import pk.nust.cuas.orchestrator.domain.service.LatencyModel;
import pk.nust.cuas.orchestrator.domain.service.ModalityRoutingPolicy;
import pk.nust.cuas.orchestrator.domain.service.ScenarioLibrary;
import pk.nust.cuas.orchestrator.infrastructure.provider.MockInferenceProvider;
import pk.nust.cuas.orchestrator.infrastructure.resilience.ResilientInferenceDispatcher;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InferenceOrchestratorTest {

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final MockInferenceProvider mock = new MockInferenceProvider(
            new ScenarioLibrary(), new LatencyModel(0.35d), FailureInjector.disabled(), false);

    @Mock
    private InferenceCache cache;
    @Mock
    private InferenceEventPublisher events;

    @AfterEach
    void shutdown() {
        executor.shutdownNow();
    }

    private static AiServiceDescriptor service(String id, Modality... modalities) {
        return AiServiceDescriptor.of(new ServiceId(id), Set.of(modalities), ProviderMode.MOCK,
                Optional.empty(), Duration.ofSeconds(2), Optional.empty(), 32);
    }

    private InferenceOrchestrator orchestrator(AiServiceDescriptor... services) {
        AiServiceRegistry registry = new AiServiceRegistry(List.of(services));
        return new InferenceOrchestrator(
                new ModalityRoutingPolicy(registry), new CanaryPolicy(),
                new ProviderSelector(List.of(mock)),
                new ResilientInferenceDispatcher(CircuitBreakerRegistry.ofDefaults(),
                        BulkheadRegistry.ofDefaults(), mock, (id, reason) -> { }, executor),
                cache, events);
    }

    private static InferenceRequest requestWith(SensorModality... modalities) {
        InferenceRequest.Builder builder = InferenceRequest.newBuilder()
                .setRequestId(UUID.randomUUID().toString())
                .setTrackId("track-" + UUID.randomUUID())
                .setCorrelationId("corr-1");
        int seq = 1;
        for (SensorModality modality : modalities) {
            builder.addDetections(DetectionRecord.newBuilder()
                    .setDetectionId("d" + seq)
                    .setSensorId("s" + seq)
                    .setSensorSeq(seq)
                    .setModality(modality));
            seq++;
        }
        return builder.build();
    }

    @Test
    void aRequestReachesEveryServiceItsModalitiesRouteTo() {
        when(cache.get(any(), any())).thenReturn(Optional.empty());

        List<InferenceResult> results = orchestrator(
                service("alpha", Modality.RF), service("beta", Modality.EO_IR))
                .infer(requestWith(SensorModality.MODALITY_RF, SensorModality.MODALITY_EO_IR));

        assertThat(results).extracting(r -> r.service().value()).containsExactly("alpha", "beta");
    }

    @Test
    void aRequestWhoseModalitiesRouteNowhereReturnsNothingAndIsNotAnError() {
        List<InferenceResult> results =
                orchestrator(service("alpha", Modality.RF)).infer(requestWith(SensorModality.MODALITY_GNSS));

        assertThat(results).isEmpty();
        verify(events, never()).publishRequested(any(), any());
    }

    @Test
    void aMultiModalServiceIsCalledOncePerRequest() {
        when(cache.get(any(), any())).thenReturn(Optional.empty());

        List<InferenceResult> results = orchestrator(service("fusion", Modality.RF, Modality.RADAR))
                .infer(requestWith(SensorModality.MODALITY_RF, SensorModality.MODALITY_RADAR));

        assertThat(results).hasSize(1);
    }

    @Test
    void aCacheHitIsReturnedWithoutCallingAProvider() {
        InferenceResponse cached = InferenceResponse.newBuilder()
                .setStatus(InferenceStatus.STATUS_OK).setModelVersion("1.3.0").build();
        when(cache.get(any(), any())).thenReturn(Optional.of(cached));

        List<InferenceResult> results =
                orchestrator(service("alpha", Modality.RF)).infer(requestWith(SensorModality.MODALITY_RF));

        assertThat(results.get(0).response().getModelVersion()).isEqualTo("1.3.0");
        assertThat(results.get(0).response().getDevice()).isNotEqualTo(ComputeDevice.DEVICE_MOCK);
    }

    @Test
    void aCacheHitDoesNotEmitRequested() {
        // That topic measures how much inference the system actually asks for; counting an answer
        // that never reached a provider would overstate demand and hide the cache's value.
        when(cache.get(any(), any())).thenReturn(Optional.of(
                InferenceResponse.newBuilder().setStatus(InferenceStatus.STATUS_OK).build()));

        orchestrator(service("alpha", Modality.RF)).infer(requestWith(SensorModality.MODALITY_RF));

        verify(events, never()).publishRequested(any(), any());
        verify(events).publishOutcome(any(), any());
    }

    @Test
    void aMissDispatchesAndCachesTheResult() {
        when(cache.get(any(), any())).thenReturn(Optional.empty());

        orchestrator(service("alpha", Modality.RF)).infer(requestWith(SensorModality.MODALITY_RF));

        verify(events).publishRequested(any(), any());
        verify(cache).put(any(), any(), any());
    }

    @Test
    void aDegradedResultIsNeverCached() {
        // A fallback answer describes the service's health at one moment, not the input. Caching it
        // would keep serving a mock answer for a full TTL after the real service recovered.
        when(cache.get(any(), any())).thenReturn(Optional.empty());
        AiServiceDescriptor grpcService = AiServiceDescriptor.of(new ServiceId("alpha"),
                Set.of(Modality.RF), ProviderMode.GRPC, Optional.of("nowhere:1"),
                Duration.ofSeconds(2), Optional.empty(), 32);
        AiServiceRegistry registry = new AiServiceRegistry(List.of(grpcService));
        // A gRPC provider that always fails, so the dispatcher must fall back.
        var failing = new pk.nust.cuas.orchestrator.application.port.InferenceProvider() {
            @Override
            public InferenceResponse infer(AiServiceDescriptor s, InferenceRequest r) {
                throw new IllegalStateException("connection refused");
            }

            @Override
            public pk.nust.cuas.inference.v1.ModelInfo modelInfo(AiServiceDescriptor s) {
                return pk.nust.cuas.inference.v1.ModelInfo.getDefaultInstance();
            }

            @Override
            public boolean isHealthy(AiServiceDescriptor s) {
                return false;
            }

            @Override
            public ProviderMode mode() {
                return ProviderMode.GRPC;
            }
        };
        InferenceOrchestrator orchestrator = new InferenceOrchestrator(
                new ModalityRoutingPolicy(registry), new CanaryPolicy(),
                new ProviderSelector(List.of(mock, failing)),
                new ResilientInferenceDispatcher(CircuitBreakerRegistry.ofDefaults(),
                        BulkheadRegistry.ofDefaults(), mock, (id, reason) -> { }, executor),
                cache, events);

        List<InferenceResult> results = orchestrator.infer(requestWith(SensorModality.MODALITY_RF));

        assertThat(results.get(0).isDegraded()).isTrue();
        verify(cache, never()).put(any(), any(), any());
    }

    @Test
    void anUnrecognisedModalityContributesNoRoute() {
        List<InferenceResult> results = orchestrator(service("alpha", Modality.RF))
                .infer(requestWith(SensorModality.MODALITY_UNSPECIFIED));

        assertThat(results).isEmpty();
    }

    @Test
    void everyDispatchPublishesAnOutcome() {
        when(cache.get(any(), any())).thenReturn(Optional.empty());

        orchestrator(service("alpha", Modality.RF), service("beta", Modality.RF))
                .infer(requestWith(SensorModality.MODALITY_RF));

        verify(events, org.mockito.Mockito.times(2)).publishOutcome(any(), any());
    }
}
