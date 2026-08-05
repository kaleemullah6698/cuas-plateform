package pk.nust.cuas.orchestrator.infrastructure.provider;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import pk.nust.cuas.inference.v1.ComputeDevice;
import pk.nust.cuas.inference.v1.InferenceOptions;
import pk.nust.cuas.inference.v1.InferenceRequest;
import pk.nust.cuas.inference.v1.InferenceResponse;
import pk.nust.cuas.inference.v1.InferenceStatus;
import pk.nust.cuas.orchestrator.domain.model.AiServiceDescriptor;
import pk.nust.cuas.orchestrator.domain.model.Modality;
import pk.nust.cuas.orchestrator.domain.model.ProviderMode;
import pk.nust.cuas.orchestrator.domain.model.ServiceId;
import pk.nust.cuas.orchestrator.domain.service.FailureInjector;
import pk.nust.cuas.orchestrator.domain.service.LatencyModel;
import pk.nust.cuas.orchestrator.domain.service.ScenarioLibrary;

class MockInferenceProviderTest {

    /** Latency simulation off: these assert on content, not on elapsed time. */
    private MockInferenceProvider provider(FailureInjector failures) {
        return new MockInferenceProvider(
                new ScenarioLibrary(), new LatencyModel(0.35d), failures, false);
    }

    private MockInferenceProvider provider() {
        return provider(FailureInjector.disabled());
    }

    private static AiServiceDescriptor service(String id, Optional<String> pinnedVersion) {
        return AiServiceDescriptor.of(new ServiceId(id), Set.of(Modality.RF), ProviderMode.MOCK,
                Optional.empty(), Duration.ofMillis(100), pinnedVersion, 32);
    }

    private static AiServiceDescriptor service(String id) {
        return service(id, Optional.empty());
    }

    private static InferenceRequest request(String trackId) {
        return request(trackId, 0.0f);
    }

    private static InferenceRequest request(String trackId, float minConfidence) {
        return InferenceRequest.newBuilder()
                .setRequestId(UUID.randomUUID().toString())
                .setTrackId(trackId)
                .setCorrelationId("corr-1")
                .setOptions(InferenceOptions.newBuilder().setMinConfidence(minConfidence))
                .build();
    }

    @Test
    void everyResponseIsBadgedAsMock() {
        // The safety property: a mocked threat number must never be mistakable for a real one.
        InferenceResponse response = provider().infer(service("any-service"), request("track-1"));

        assertThat(response.getModelVersion()).startsWith("0.0.0-mock");
        assertThat(response.getModelSha()).isEqualTo("mock");
        assertThat(response.getDevice()).isEqualTo(ComputeDevice.DEVICE_MOCK);
    }

    @Test
    void aPinnedVersionNeverReplacesTheMockBadge() {
        // A canary pin must not let the mock claim to be 1.3.0.
        InferenceResponse response =
                provider().infer(service("any-service", Optional.of("1.3.0")), request("track-1"));

        assertThat(response.getModelVersion()).startsWith("0.0.0-mock").contains("1.3.0");
    }

    @Test
    void theResponseEchoesTheRequestIdentity() {
        InferenceRequest request = request("track-42");

        InferenceResponse response = provider().infer(service("any-service"), request);

        assertThat(response.getRequestId()).isEqualTo(request.getRequestId());
        assertThat(response.getTrackId()).isEqualTo("track-42");
    }

    @Test
    void theModelIdIsTheServiceIdSoTelemetryCanAttributeTheResult() {
        assertThat(provider().infer(service("some-service"), request("t")).getModelId())
                .isEqualTo("some-service");
    }

    @Test
    void theSameTrackAlwaysProducesTheSameResult() {
        MockInferenceProvider provider = provider();
        AiServiceDescriptor service = service("any-service");

        InferenceResponse first = provider.infer(service, request("stable-track"));
        for (int i = 0; i < 20; i++) {
            InferenceResponse again = provider.infer(service, request("stable-track"));
            assertThat(again.getStatus()).isEqualTo(first.getStatus());
            assertThat(again.getPredictionsList()).isEqualTo(first.getPredictionsList());
            assertThat(again.getConfidence()).isEqualTo(first.getConfidence());
            assertThat(again.getInferenceMs()).isEqualTo(first.getInferenceMs());
        }
    }

    @Test
    void oneMockServesEveryServiceWithoutKnowingAnyOfThem() {
        // The coupling section 6.1 forbids: a mock that varied by service would have to know what
        // each service models.
        MockInferenceProvider provider = provider();

        for (String id : new String[] {"a", "b", "a-model-nobody-has-written-yet"}) {
            InferenceResponse response = provider.infer(service(id), request("track-1"));
            assertThat(response.getModelId()).isEqualTo(id);
            assertThat(response.getDevice()).isEqualTo(ComputeDevice.DEVICE_MOCK);
        }
    }

    @Test
    void aReportedLatencyIsAlwaysPresentEvenWhenNotSleeping() {
        assertThat(provider().infer(service("any-service"), request("t")).getInferenceMs())
                .isPositive();
    }

    @Test
    void latencyIsActuallySimulatedWhenEnabled() {
        MockInferenceProvider sleeping = new MockInferenceProvider(
                new ScenarioLibrary(), new LatencyModel(0.35d), FailureInjector.disabled(), true);

        long start = System.nanoTime();
        InferenceResponse response = sleeping.infer(service("any-service"), request("track-1"));
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertThat(elapsedMs)
                .as("a mock that returns instantly makes every timeout and breaker untestable")
                .isGreaterThanOrEqualTo(response.getInferenceMs() - 2);
    }

    @Test
    void aMinimumConfidenceSuppressesWeakerPredictions() {
        MockInferenceProvider provider = provider();
        AiServiceDescriptor service = service("any-service");
        // A track whose scenario yields several ranked predictions.
        String trackId = trackWithMultiplePredictions(provider, service);

        InferenceResponse unfiltered = provider.infer(service, request(trackId, 0.0f));
        InferenceResponse filtered = provider.infer(service, request(trackId, 0.9f));

        assertThat(filtered.getPredictionsCount()).isLessThan(unfiltered.getPredictionsCount());
        assertThat(filtered.getPredictionsList())
                .allSatisfy(p -> assertThat(p.getConfidence()).isGreaterThanOrEqualTo(0.9f));
    }

    @Test
    void anInjectedTimeoutIsReportedAsAStatusNotThrown() {
        // A provider throws only on transport failure; an injected timeout is still a response the
        // resilience layer classifies. Throwing here would conflate the two.
        MockInferenceProvider provider = provider(new FailureInjector(1.0d, 0.0d, 0.0d));

        InferenceResponse response = provider.infer(service("any-service"), request("t"));

        assertThat(response.getStatus()).isEqualTo(InferenceStatus.STATUS_TIMEOUT);
        assertThat(response.getErrorMessage()).isNotBlank();
    }

    @Test
    void anInjectedModelErrorReportsItsStatusAndCarriesNoPredictions() {
        MockInferenceProvider provider = provider(new FailureInjector(0.0d, 1.0d, 0.0d));

        InferenceResponse response = provider.infer(service("any-service"), request("t"));

        assertThat(response.getStatus()).isEqualTo(InferenceStatus.STATUS_MODEL_ERROR);
        assertThat(response.getPredictionsList()).isEmpty();
    }

    @Test
    void anInjectedLowConfidenceStillCarriesPredictions() {
        // It ran; it was merely unsure. An empty result would misrepresent that.
        MockInferenceProvider provider = provider(new FailureInjector(0.0d, 0.0d, 1.0d));

        InferenceResponse response = provider.infer(service("any-service"), request("t"));

        assertThat(response.getStatus()).isEqualTo(InferenceStatus.STATUS_LOW_CONFIDENCE);
        assertThat(response.getPredictionsList()).isNotEmpty();
        assertThat(response.getConfidence().getIsReliable()).isFalse();
    }

    @Test
    void theMockIsAlwaysHealthyBecauseAFallbackThatCanFailIsNoFallback() {
        assertThat(provider().isHealthy(service("any-service"))).isTrue();
    }

    @Test
    void modelInfoIsBadgedAndDescribesTheServiceItStandsInFor() {
        var info = provider().modelInfo(service("some-service"));

        assertThat(info.getModelId()).isEqualTo("some-service");
        assertThat(info.getModelVersion()).isEqualTo("0.0.0-mock");
        assertThat(info.getFramework()).isEqualTo("mock");
        assertThat(info.getRequiresGpu()).isFalse();
        assertThat(info.getP95LatencyMs()).isEqualTo(100);
    }

    @Test
    void theProviderDeclaresItsMode() {
        assertThat(provider().mode()).isEqualTo(ProviderMode.MOCK);
    }

    @Test
    void everyResponseCarriesAConfidenceReport() {
        var confidence = provider().infer(service("any-service"), request("t")).getConfidence();

        assertThat(confidence.getMethod()).isEqualTo("mock");
        assertThat(confidence.getScore()).isBetween(0.0f, 1.0f);
    }

    /** Finds a track whose scenario produces more than one prediction, deterministically. */
    private static String trackWithMultiplePredictions(
            MockInferenceProvider provider, AiServiceDescriptor service) {
        for (int i = 0; i < 200; i++) {
            String trackId = "track-" + i;
            if (provider.infer(service, request(trackId)).getPredictionsCount() > 1) {
                return trackId;
            }
        }
        throw new IllegalStateException("no scenario produced multiple predictions");
    }
}
