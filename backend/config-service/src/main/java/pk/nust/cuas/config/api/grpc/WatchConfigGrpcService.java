package pk.nust.cuas.config.api.grpc;

import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import pk.nust.cuas.config.application.ConfigurationService;
import pk.nust.cuas.config.domain.model.ConfigNamespaceKind;
import pk.nust.cuas.config.domain.model.Configuration;
import pk.nust.cuas.config.infrastructure.messaging.ConfigChangeBroadcaster;
import pk.nust.cuas.config.infrastructure.messaging.ConfigProtoMapper;
import pk.nust.cuas.config.v1.ConfigNamespace;
import pk.nust.cuas.config.v1.ConfigServiceGrpc;
import pk.nust.cuas.config.v1.ConfigUpdated;
import pk.nust.cuas.config.v1.WatchConfigRequest;

/**
 * The {@code WatchConfig} server stream (ARCHITECTURE.md §4.1.12): a subscriber receives the current
 * value of every namespace it watches, then every subsequent change, on one open stream — which is
 * what makes configuration pushed, not polled in the hot path (§4.2 rule 3).
 *
 * <p>The current snapshot is sent and the live subscription registered under one lock, so no change is
 * ever delivered before the snapshot completes and none is lost in the gap between them. Every write
 * to the stream is serialised on that lock, because a {@link StreamObserver} is not safe for
 * concurrent use. When the client cancels, the subscription is removed.
 *
 * <p>Authorisation is transport-level (§7.2): {@code Service ↔ Service} gRPC is mTLS with workload
 * identity, the same protection the platform's other inter-service gRPC surfaces rely on. The
 * operator-facing surface is the REST API, which carries {@code @PreAuthorize}.
 */
@Component
public class WatchConfigGrpcService extends ConfigServiceGrpc.ConfigServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(WatchConfigGrpcService.class);

    private final ConfigurationService configurations;
    private final ConfigChangeBroadcaster broadcaster;
    private final ConfigProtoMapper mapper;

    public WatchConfigGrpcService(ConfigurationService configurations,
                                  ConfigChangeBroadcaster broadcaster, ConfigProtoMapper mapper) {
        this.configurations = configurations;
        this.broadcaster = broadcaster;
        this.mapper = mapper;
    }

    @Override
    public void watchConfig(WatchConfigRequest request, StreamObserver<ConfigUpdated> responseObserver) {
        Set<String> namespaces = Set.copyOf(request.getNamespacesList());
        Set<ConfigNamespaceKind> kinds = request.getKindsList().stream()
                .map(WatchConfigGrpcService::toDomainKind)
                .filter(k -> k != null)
                .collect(Collectors.toSet());
        Predicate<Configuration> matches = configuration ->
                (namespaces.isEmpty() && kinds.isEmpty())
                        || namespaces.contains(configuration.namespace())
                        || kinds.contains(configuration.kind());

        ServerCallStreamObserver<ConfigUpdated> stream =
                (ServerCallStreamObserver<ConfigUpdated>) responseObserver;
        Object lock = new Object();

        Consumer<Configuration> listener = configuration -> {
            if (!matches.test(configuration)) {
                return;
            }
            synchronized (lock) {
                if (!stream.isCancelled()) {
                    stream.onNext(mapper.toProto(configuration));
                }
            }
        };

        synchronized (lock) {
            configurations.snapshot(Set.of()).stream()
                    .filter(matches)
                    .forEach(configuration -> stream.onNext(mapper.toProto(configuration)));
            Runnable unsubscribe = broadcaster.subscribe(listener);
            stream.setOnCancelHandler(unsubscribe);
        }
        log.debug("WatchConfig subscriber '{}' watching {} namespaces / {} kinds",
                request.getSubscriberId(), namespaces.size(), kinds.size());
    }

    private static ConfigNamespaceKind toDomainKind(ConfigNamespace kind) {
        return switch (kind) {
            case CONFIG_NAMESPACE_SENSOR -> ConfigNamespaceKind.SENSOR;
            case CONFIG_NAMESPACE_POLICY -> ConfigNamespaceKind.POLICY;
            case CONFIG_NAMESPACE_AI -> ConfigNamespaceKind.AI;
            case CONFIG_NAMESPACE_FEATURE_FLAG -> ConfigNamespaceKind.FEATURE_FLAG;
            case CONFIG_NAMESPACE_RETENTION -> ConfigNamespaceKind.RETENTION;
            case CONFIG_NAMESPACE_PROTECTED_ASSET -> ConfigNamespaceKind.PROTECTED_ASSET;
            case CONFIG_NAMESPACE_GENERAL -> ConfigNamespaceKind.GENERAL;
            default -> null;  // UNSPECIFIED / UNRECOGNIZED — ignored as a filter
        };
    }
}
