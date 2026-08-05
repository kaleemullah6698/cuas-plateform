package pk.nust.cuas.orchestrator.infrastructure.provider;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pk.nust.cuas.inference.v1.HealthRequest;
import pk.nust.cuas.inference.v1.HealthResponse;
import pk.nust.cuas.inference.v1.InferenceRequest;
import pk.nust.cuas.inference.v1.InferenceResponse;
import pk.nust.cuas.inference.v1.InferenceServiceGrpc;
import pk.nust.cuas.inference.v1.ModelInfo;
import pk.nust.cuas.inference.v1.ModelInfoRequest;
import pk.nust.cuas.orchestrator.application.port.InferenceProvider;
import pk.nust.cuas.orchestrator.domain.model.AiServiceDescriptor;
import pk.nust.cuas.orchestrator.domain.model.ProviderMode;

/**
 * Calls a real AI service over the {@code InferenceService} gRPC contract (ARCHITECTURE.md sections
 * 6.1, 6.2).
 *
 * <p><b>One instance serves every gRPC service.</b> The endpoint comes from the descriptor, and
 * channels are created on demand and cached per endpoint. A provider bean per model would be exactly
 * the code change §6.1 forbids, and would also mean a new model could not be added without a
 * redeploy.
 *
 * <p>Channels are cached because a gRPC channel is an expensive, long-lived object holding
 * connections and load-balancer state; creating one per call would add a TCP and TLS handshake to
 * every inference and defeat the latency budget entirely.
 *
 * <p><b>Deadlines are set on the stub as well as by the dispatcher.</b> The dispatcher's time limiter
 * stops the orchestrator waiting; the gRPC deadline additionally tells the *server* to stop working.
 * Without it a timed-out call would leave a GPU computing a result nobody will read — which under
 * load is how one slow service becomes a saturated one.
 *
 * <p>A transport failure throws, so the circuit breaker counts it. A response the service actually
 * produced — however unfavourable — is returned unchanged, because that is the model's answer rather
 * than evidence the service is unreachable.
 *
 * <p>Transport security is §7.2's: {@code Service ↔ Service} is mTLS with SPIFFE-style workload
 * identity, and §7.1 additionally restricts AI services to accepting only the orchestrator's
 * certificate. Certificates are supplied by the platform's deployment (internal CA, 24-hour TTL);
 * this client uses plaintext when none is configured, which is the development posture.
 */
public class GrpcInferenceProvider implements InferenceProvider, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(GrpcInferenceProvider.class);

    private final Map<String, ManagedChannel> channels = new ConcurrentHashMap<>();
    private final boolean plaintext;

    /**
     * @param plaintext whether to connect without TLS. True in development; production supplies
     *                  mTLS material per §7.2, and the channel is built accordingly.
     */
    public GrpcInferenceProvider(boolean plaintext) {
        this.plaintext = plaintext;
    }

    @Override
    public InferenceResponse infer(AiServiceDescriptor service, InferenceRequest request) {
        try {
            return stub(service).infer(request);
        } catch (StatusRuntimeException ex) {
            // Transport-level. Thrown so the breaker counts it; the dispatcher turns it into a
            // fallback rather than letting it reach the caller.
            throw new IllegalStateException(
                    "gRPC inference failed on " + service.id() + ": " + ex.getStatus(), ex);
        }
    }

    @Override
    public ModelInfo modelInfo(AiServiceDescriptor service) {
        try {
            return stub(service).getModelInfo(ModelInfoRequest.newBuilder()
                    .setModelId(service.id().value())
                    .setModelVersion(service.modelVersion().orElse(""))
                    .build());
        } catch (StatusRuntimeException ex) {
            throw new IllegalStateException(
                    "gRPC model info failed on " + service.id() + ": " + ex.getStatus(), ex);
        }
    }

    /**
     * Probes the service's health.
     *
     * <p>Returns false rather than throwing: this drives {@code GET /ai/services}, where an
     * unreachable service is a *fact to report*, not an error to propagate. An admin asking which
     * services are up must get an answer about all of them, not a 500 because one is down.
     */
    @Override
    public boolean isHealthy(AiServiceDescriptor service) {
        try {
            HealthResponse response = stub(service).healthCheck(
                    HealthRequest.newBuilder().setModelId(service.id().value()).build());
            return response.getStatus() == HealthResponse.ServingStatus.SERVING;
        } catch (RuntimeException ex) {
            log.debug("Health check failed for {}", service.id(), ex);
            return false;
        }
    }

    @Override
    public ProviderMode mode() {
        return ProviderMode.GRPC;
    }

    private InferenceServiceGrpc.InferenceServiceBlockingStub stub(AiServiceDescriptor service) {
        String endpoint = service.endpoint().orElseThrow(() -> new IllegalStateException(
                "service " + service.id() + " is mode=grpc with no endpoint"));
        ManagedChannel channel = channels.computeIfAbsent(endpoint, this::openChannel);
        return InferenceServiceGrpc.newBlockingStub(channel)
                .withDeadlineAfter(service.timeout().toMillis(), TimeUnit.MILLISECONDS);
    }

    private ManagedChannel openChannel(String endpoint) {
        log.info("Opening gRPC channel to {}", endpoint);
        ManagedChannelBuilder<?> builder = ManagedChannelBuilder.forTarget(endpoint);
        if (plaintext) {
            builder.usePlaintext();
        }
        return builder.build();
    }

    @Override
    public void close() {
        channels.values().forEach(channel -> {
            channel.shutdown();
            try {
                if (!channel.awaitTermination(5, TimeUnit.SECONDS)) {
                    channel.shutdownNow();
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                channel.shutdownNow();
            }
        });
        channels.clear();
    }
}
