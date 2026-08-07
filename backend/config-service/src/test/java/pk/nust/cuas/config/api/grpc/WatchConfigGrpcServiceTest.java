package pk.nust.cuas.config.api.grpc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.grpc.stub.ServerCallStreamObserver;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import pk.nust.cuas.config.application.ConfigurationService;
import pk.nust.cuas.config.domain.model.ConfigNamespaceKind;
import pk.nust.cuas.config.domain.model.Configuration;
import pk.nust.cuas.config.infrastructure.messaging.ConfigChangeBroadcaster;
import pk.nust.cuas.config.infrastructure.messaging.ConfigProtoMapper;
import pk.nust.cuas.config.v1.ConfigUpdated;
import pk.nust.cuas.config.v1.WatchConfigRequest;

/**
 * The WatchConfig handler's contract: send the current snapshot, then every subsequent change, with
 * the requested filter applied — exercised without a listening server by driving the handler and the
 * broadcaster directly.
 */
@SuppressWarnings("unchecked")
class WatchConfigGrpcServiceTest {

    private final ConfigurationService configurations = mock(ConfigurationService.class);
    private final ConfigChangeBroadcaster broadcaster = new ConfigChangeBroadcaster();
    private final WatchConfigGrpcService service =
            new WatchConfigGrpcService(configurations, broadcaster, new ConfigProtoMapper());

    @Test
    void itSendsTheSnapshotThenLiveChanges() {
        when(configurations.snapshot(Set.of())).thenReturn(List.of(config("policy", "{\"a\":1}")));
        ServerCallStreamObserver<ConfigUpdated> stream = mock(ServerCallStreamObserver.class);
        when(stream.isCancelled()).thenReturn(false);

        service.watchConfig(WatchConfigRequest.getDefaultInstance(), stream);

        // 1) the current snapshot
        ArgumentCaptor<ConfigUpdated> captor = ArgumentCaptor.forClass(ConfigUpdated.class);
        verify(stream).onNext(captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getNamespace()).isEqualTo("policy");

        // 2) a subsequent change arrives on the same stream
        broadcaster.notifyChanged(config("policy", "{\"a\":2}"));
        verify(stream, times(2)).onNext(any());
    }

    @Test
    void itAppliesTheNamespaceFilterToLiveChanges() {
        when(configurations.snapshot(Set.of())).thenReturn(List.of());
        ServerCallStreamObserver<ConfigUpdated> stream = mock(ServerCallStreamObserver.class);
        when(stream.isCancelled()).thenReturn(false);
        WatchConfigRequest request = WatchConfigRequest.newBuilder().addNamespaces("policy").build();

        service.watchConfig(request, stream);
        broadcaster.notifyChanged(config("sensor", "{}"));   // not watched — ignored
        broadcaster.notifyChanged(config("policy", "{}"));   // watched — delivered

        ArgumentCaptor<ConfigUpdated> captor = ArgumentCaptor.forClass(ConfigUpdated.class);
        verify(stream).onNext(captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getNamespace()).isEqualTo("policy");
    }

    private static Configuration config(String namespace, String value) {
        return Configuration.initial(namespace, ConfigNamespaceKind.POLICY, value, "u", "j", Instant.now());
    }
}
