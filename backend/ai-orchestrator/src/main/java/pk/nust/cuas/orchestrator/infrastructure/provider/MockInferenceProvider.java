package pk.nust.cuas.orchestrator.infrastructure.provider;

import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pk.nust.cuas.inference.v1.ComputeDevice;
import pk.nust.cuas.inference.v1.ConfidenceReport;
import pk.nust.cuas.inference.v1.InferenceRequest;
import pk.nust.cuas.inference.v1.InferenceResponse;
import pk.nust.cuas.inference.v1.ModelInfo;
import pk.nust.cuas.inference.v1.Prediction;
import pk.nust.cuas.orchestrator.application.port.InferenceProvider;
import pk.nust.cuas.orchestrator.common.InferenceStatusMapper;
import pk.nust.cuas.orchestrator.domain.model.AiServiceDescriptor;
import pk.nust.cuas.orchestrator.domain.model.InferenceOutcome;
import pk.nust.cuas.orchestrator.domain.model.MockScenario;
import pk.nust.cuas.orchestrator.domain.model.ProviderMode;
import pk.nust.cuas.orchestrator.domain.model.SyntheticOutcome;
import pk.nust.cuas.orchestrator.domain.model.SyntheticPrediction;
import pk.nust.cuas.orchestrator.domain.service.FailureInjector;
import pk.nust.cuas.orchestrator.domain.service.LatencyModel;
import pk.nust.cuas.orchestrator.domain.service.ScenarioLibrary;

/**
 * The mock inference provider (ARCHITECTURE.md section 6.4).
 *
 * <p><b>A permanent, first-class component — not a stub and not a test double.</b> It is what lets
 * the whole platform run end to end with zero trained models, and it stays after real models ship: it
 * backs CI, integration tests, demonstrations, regression tests, and offline development, and it is
 * the fallback an open circuit breaker falls onto (section 6.5).
 *
 * <p><b>It is badged, always.</b> Every response reports {@code model_version: 0.0.0-mock} and
 * {@code device: DEVICE_MOCK}, from which the dashboard renders an explicit MOCK INFERENCE badge. An
 * operator, a reviewer and a demo audience can therefore always tell a synthetic number from a real
 * one. For a defence-adjacent system that is a safety property, not a nicety — a mocked threat score
 * mistaken for a real one is exactly the failure this platform must not have.
 *
 * <p><b>It serves every registered service, and knows none of them.</b> The descriptor arrives as a
 * parameter, so the same instance stands in for any model, present or future. A mock that varied by
 * service would have to know what each service models, which is the coupling section 6.1 forbids.
 *
 * <p>Everything it decides is deterministic, seeded by track and service id: which scenario, how long
 * it took, whether it failed. The same track always behaves identically, so a demonstration replays
 * and a failing test reproduces.
 */
public class MockInferenceProvider implements InferenceProvider {

    private static final Logger log = LoggerFactory.getLogger(MockInferenceProvider.class);

    /** Never mistakable for a real model version (section 6.4). */
    public static final String MOCK_VERSION = "0.0.0-mock";
    public static final String MOCK_SHA = "mock";

    private final ScenarioLibrary scenarios;
    private final LatencyModel latency;
    private final FailureInjector failures;
    private final boolean simulateLatency;

    /**
     * @param scenarios       the scenario library
     * @param latency         the latency distribution
     * @param failures        the failure injector
     * @param simulateLatency whether to actually sleep for the sampled time. The sampled value is
     *                        reported either way; sleeping is what makes a timeout and a breaker
     *                        genuinely fire, and is switched off in unit tests that only assert on
     *                        content.
     */
    public MockInferenceProvider(ScenarioLibrary scenarios, LatencyModel latency,
                                 FailureInjector failures, boolean simulateLatency) {
        this.scenarios = scenarios;
        this.latency = latency;
        this.failures = failures;
        this.simulateLatency = simulateLatency;
    }

    @Override
    public InferenceResponse infer(AiServiceDescriptor service, InferenceRequest request) {
        String serviceId = service.id().value();
        String trackId = request.getTrackId();

        Duration sampled = latency.sample(service.timeout(), trackId, serviceId);
        sleepIfSimulating(sampled);

        SyntheticOutcome outcome = resolveOutcome(trackId, serviceId);
        return toResponse(service, request, outcome, sampled);
    }

    @Override
    public ModelInfo modelInfo(AiServiceDescriptor service) {
        return ModelInfo.newBuilder()
                .setModelId(service.id().value())
                .setModelVersion(MOCK_VERSION)
                .setModelSha(MOCK_SHA)
                .setFramework("mock")
                .setTrainedOn("none — synthetic scenarios")
                .setP95LatencyMs((int) service.timeout().toMillis())
                .setRequiresGpu(false)
                .build();
    }

    /**
     * Always healthy. The mock has no dependency that can fail, which is precisely why it is a usable
     * fallback: a fallback that could itself be unavailable would leave the orchestrator with nothing
     * to answer with.
     */
    @Override
    public boolean isHealthy(AiServiceDescriptor service) {
        return true;
    }

    @Override
    public ProviderMode mode() {
        return ProviderMode.MOCK;
    }

    /**
     * An injected failure overrides the scenario; otherwise the scenario's own outcome stands.
     *
     * <p>Injection wins deliberately: it exists to force a state the scenario would not otherwise
     * produce, and a scenario that could veto it would make the rates unreachable.
     */
    private SyntheticOutcome resolveOutcome(String trackId, String serviceId) {
        Optional<InferenceOutcome> injected = failures.inject(trackId, serviceId);
        if (injected.isPresent()) {
            InferenceOutcome outcome = injected.get();
            log.debug("Mock injected {} for track {} on {}", outcome, trackId, serviceId);
            return outcome == InferenceOutcome.LOW_CONFIDENCE
                    ? lowConfidenceOf(trackId)
                    : SyntheticOutcome.failure(outcome, "injected " + outcome.name().toLowerCase());
        }
        return scenarios.outcomeOf(scenarios.scenarioFor(trackId));
    }

    /** An injected low-confidence result still carries predictions — it ran, it was just unsure. */
    private SyntheticOutcome lowConfidenceOf(String trackId) {
        SyntheticOutcome base = scenarios.outcomeOf(MockScenario.LOW_CONFIDENCE_AMBIGUOUS);
        log.trace("Mock injected low confidence for track {}", trackId);
        return base;
    }

    private void sleepIfSimulating(Duration sampled) {
        if (!simulateLatency) {
            return;
        }
        try {
            Thread.sleep(sampled.toMillis(), sampled.toNanosPart() % 1_000_000);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while simulating inference latency", ex);
        }
    }

    private InferenceResponse toResponse(AiServiceDescriptor service, InferenceRequest request,
                                         SyntheticOutcome outcome, Duration sampled) {
        InferenceResponse.Builder builder = InferenceResponse.newBuilder()
                .setRequestId(request.getRequestId())
                .setTrackId(request.getTrackId())
                .setStatus(InferenceStatusMapper.toProto(outcome.outcome()))
                .setModelId(service.id().value())
                // The version a caller pinned is echoed only as a suffix, never as a bare version:
                // a mock must not be able to claim it is 1.3.0.
                .setModelVersion(mockVersionFor(service))
                .setModelSha(MOCK_SHA)
                .setInferenceMs((int) sampled.toMillis())
                .setQueueWaitMs(0)
                .setDevice(ComputeDevice.DEVICE_MOCK)
                .setErrorMessage(outcome.errorMessage());

        for (SyntheticPrediction prediction : outcome.predictions()) {
            // A caller's min_confidence suppresses weaker predictions, exactly as a real service must.
            if (prediction.confidence() < request.getOptions().getMinConfidence()) {
                continue;
            }
            builder.addPredictions(Prediction.newBuilder()
                    .setLabel(prediction.label())
                    .setLabelClass(prediction.labelClass())
                    .setConfidence(prediction.confidence())
                    .setRank(prediction.rank())
                    .putAllAttributes(prediction.attributes()));
        }

        builder.setConfidence(ConfidenceReport.newBuilder()
                .setScore(outcome.confidence())
                .setCalibratedScore(outcome.confidence())
                .setEntropy(outcome.entropy())
                .setMethod("mock")
                .setIsReliable(outcome.reliable()));

        return builder.build();
    }

    /**
     * The reported version. A pinned version is appended so a canary is visible in the telemetry,
     * but {@code 0.0.0-mock} always leads — nothing downstream can read this as a real model version.
     */
    private static String mockVersionFor(AiServiceDescriptor service) {
        return service.modelVersion()
                .map(pinned -> MOCK_VERSION + "+pinned-" + pinned)
                .orElse(MOCK_VERSION);
    }
}
