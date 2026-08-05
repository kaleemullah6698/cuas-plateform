package pk.nust.cuas.orchestrator.application;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import pk.nust.cuas.detection.v1.DetectionRecord;
import pk.nust.cuas.inference.v1.InferenceRequest;
import pk.nust.cuas.inference.v1.InferenceResponse;
import pk.nust.cuas.orchestrator.application.port.InferenceCache;
import pk.nust.cuas.orchestrator.application.port.InferenceEventPublisher;
import pk.nust.cuas.orchestrator.application.port.InferenceProvider;
import pk.nust.cuas.orchestrator.common.ModalityMapper;
import pk.nust.cuas.orchestrator.domain.model.AiServiceDescriptor;
import pk.nust.cuas.orchestrator.domain.model.InferenceResult;
import pk.nust.cuas.orchestrator.domain.model.Modality;
import pk.nust.cuas.orchestrator.domain.service.ModalityRoutingPolicy;
import pk.nust.cuas.orchestrator.domain.service.RequestContentHash;
import pk.nust.cuas.orchestrator.infrastructure.resilience.ResilientInferenceDispatcher;

/**
 * Runs one inference request across every service its modalities route to (ARCHITECTURE.md sections
 * 4.1.6, 6.5).
 *
 * <p>The sequence, matching §6.5's diagram: resolve the services by modality, check the
 * content-addressed cache, dispatch what is left under the resilience layer, and publish the
 * outcomes. It never names a model — it asks the routing policy which descriptors apply and hands
 * each to the provider bound to its mode.
 *
 * <p><b>A cache hit does not emit {@code requested}.</b> That topic measures how much inference the
 * system actually asks for; counting an answer that never reached a provider would overstate demand
 * and make the cache look useless in the one metric that should show its value. The *completed* event
 * is still emitted, because downstream needs the result either way.
 *
 * <p><b>Degraded results are never cached</b> (§6.5). A fallback answer describes the service's health
 * at one moment, not the input; caching it would keep serving a mock answer for a full TTL after the
 * real service recovered.
 */
@Service
public class InferenceOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(InferenceOrchestrator.class);

    private final ModalityRoutingPolicy routing;
    private final ProviderSelector providers;
    private final ResilientInferenceDispatcher dispatcher;
    private final InferenceCache cache;
    private final InferenceEventPublisher events;

    public InferenceOrchestrator(ModalityRoutingPolicy routing, ProviderSelector providers,
                                 ResilientInferenceDispatcher dispatcher, InferenceCache cache,
                                 InferenceEventPublisher events) {
        this.routing = routing;
        this.providers = providers;
        this.dispatcher = dispatcher;
        this.cache = cache;
        this.events = events;
    }

    /**
     * Infers across every service the request's modalities route to.
     *
     * @param request the request
     * @return one result per routed service, in configuration order; empty when nothing routes,
     *     which is a normal state rather than an error
     */
    public List<InferenceResult> infer(InferenceRequest request) {
        List<AiServiceDescriptor> services = routing.route(modalitiesOf(request));
        if (services.isEmpty()) {
            log.debug("No AI service accepts the modalities of track {}", request.getTrackId());
            return List.of();
        }

        String contentHash = contentHashOf(request);
        List<InferenceResult> results = new ArrayList<>(services.size());
        for (AiServiceDescriptor service : services) {
            results.add(inferOne(service, request, contentHash));
        }
        return List.copyOf(results);
    }

    private InferenceResult inferOne(AiServiceDescriptor service, InferenceRequest request,
                                     String contentHash) {
        Optional<InferenceResponse> cached = cache.get(service.id(), contentHash);
        if (cached.isPresent()) {
            log.debug("Inference cache hit for {} on track {}", service.id(), request.getTrackId());
            InferenceResult result = InferenceResult.primary(service.id(), cached.get());
            events.publishOutcome(request, result);
            return result;
        }

        events.publishRequested(service.id(), request);
        InferenceResult result =
                dispatcher.dispatch(service, providers.forService(service), request);

        if (!result.isDegraded()) {
            cache.put(service.id(), contentHash, result.response());
        }
        events.publishOutcome(request, result);
        return result;
    }

    /** The distinct modalities present in the request's detections. */
    private static Set<Modality> modalitiesOf(InferenceRequest request) {
        Set<Modality> modalities = new LinkedHashSet<>();
        for (DetectionRecord detection : request.getDetectionsList()) {
            ModalityMapper.toDomain(detection.getModality()).ifPresent(modalities::add);
        }
        return modalities;
    }

    /**
     * The cache key: the observation and the options, never the request or track id.
     *
     * <p>Each detection is fingerprinted by its own id and sensor sequence rather than its full
     * bytes — a detection id is already unique and immutable, so two requests carrying the same
     * detection ids are carrying the same observation, and hashing the whole record would only add
     * cost and a dependency on field ordering.
     */
    private static String contentHashOf(InferenceRequest request) {
        List<String> fingerprints = request.getDetectionsList().stream()
                .map(d -> d.getDetectionId() + '#' + d.getSensorId() + '#' + d.getSensorSeq())
                .toList();
        return RequestContentHash.of(fingerprints,
                request.getOptions().getModelVersion(),
                request.getOptions().getMinConfidence(),
                request.getOptions().getIncludeFeatures(),
                request.getOptions().getExplain());
    }
}
