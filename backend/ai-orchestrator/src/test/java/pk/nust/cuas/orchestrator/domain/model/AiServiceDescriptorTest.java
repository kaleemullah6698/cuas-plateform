package pk.nust.cuas.orchestrator.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AiServiceDescriptorTest {

    private static AiServiceDescriptor descriptor(
            ProviderMode mode, Optional<String> endpoint, Set<Modality> modalities) {
        return new AiServiceDescriptor(new ServiceId("some-service"), modalities, mode, endpoint,
                Duration.ofMillis(100), Optional.empty(), 32);
    }

    @Test
    void aServiceAcceptsExactlyTheModalitiesItDeclares() {
        AiServiceDescriptor service =
                descriptor(ProviderMode.MOCK, Optional.empty(), Set.of(Modality.RF, Modality.RADAR));

        assertThat(service.accepts(Modality.RF)).isTrue();
        assertThat(service.accepts(Modality.RADAR)).isTrue();
        assertThat(service.accepts(Modality.ACOUSTIC)).isFalse();
    }

    @Test
    void aGrpcServiceWithoutAnEndpointIsRejectedAtConstruction() {
        // It would otherwise fail on its first call, which is the worst possible moment to discover
        // a configuration error. Failing here turns an outage into a startup error.
        assertThatThrownBy(() ->
                descriptor(ProviderMode.GRPC, Optional.empty(), Set.of(Modality.RF)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("endpoint");
    }

    @Test
    void aMockServiceNeedsNoEndpoint() {
        assertThat(descriptor(ProviderMode.MOCK, Optional.empty(), Set.of(Modality.RF)).endpoint())
                .isEmpty();
    }

    @Test
    void aServiceAcceptingNoModalityIsRejected() {
        // Nothing would ever route to it, so it is certainly a configuration mistake.
        assertThatThrownBy(() -> descriptor(ProviderMode.MOCK, Optional.empty(), Set.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("modality");
    }

    @Test
    void aNonPositiveTimeoutIsRejected() {
        assertThatThrownBy(() -> new AiServiceDescriptor(new ServiceId("s"), Set.of(Modality.RF),
                ProviderMode.MOCK, Optional.empty(), Duration.ZERO, Optional.empty(), 32))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("timeout");
    }

    @Test
    void aBulkheadWithNoPermitsIsRejected() {
        assertThatThrownBy(() -> new AiServiceDescriptor(new ServiceId("s"), Set.of(Modality.RF),
                ProviderMode.MOCK, Optional.empty(), Duration.ofMillis(100), Optional.empty(), 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("permit");
    }

    @Test
    void theModalitySetIsDefensivelyCopied() {
        Set<Modality> mutable = new java.util.HashSet<>(Set.of(Modality.RF));
        AiServiceDescriptor service = descriptor(ProviderMode.MOCK, Optional.empty(), mutable);

        mutable.add(Modality.ACOUSTIC);

        assertThat(service.accepts(Modality.ACOUSTIC))
                .as("a caller cannot widen a service's routing behind the registry's back")
                .isFalse();
    }

    @Test
    void aBlankServiceIdIsRejected() {
        assertThatThrownBy(() -> new ServiceId("  ")).isInstanceOf(IllegalArgumentException.class);
    }
}
