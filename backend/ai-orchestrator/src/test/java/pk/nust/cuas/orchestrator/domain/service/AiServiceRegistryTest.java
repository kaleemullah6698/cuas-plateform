package pk.nust.cuas.orchestrator.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import pk.nust.cuas.orchestrator.domain.model.AiServiceDescriptor;
import pk.nust.cuas.orchestrator.domain.model.Modality;
import pk.nust.cuas.orchestrator.domain.model.ProviderMode;
import pk.nust.cuas.orchestrator.domain.model.ServiceId;

class AiServiceRegistryTest {

    /** Service ids are deliberately meaningless here — the registry must not care what they are. */
    private static AiServiceDescriptor service(String id, Modality... modalities) {
        return AiServiceDescriptor.of(new ServiceId(id), Set.of(modalities), ProviderMode.MOCK,
                Optional.empty(), Duration.ofMillis(100), Optional.empty(), 32);
    }

    @Test
    void aModalityRoutesToEveryServiceThatAcceptsIt() {
        AiServiceRegistry registry = new AiServiceRegistry(List.of(
                service("alpha", Modality.RF),
                service("beta", Modality.EO_IR),
                service("gamma", Modality.RF, Modality.RADAR)));

        assertThat(registry.servicesFor(Modality.RF))
                .extracting(s -> s.id().value())
                .containsExactly("alpha", "gamma");
    }

    @Test
    void routingIsInConfigurationOrderSoAFanOutIsReproducible() {
        AiServiceRegistry registry = new AiServiceRegistry(List.of(
                service("zulu", Modality.RF),
                service("alpha", Modality.RF),
                service("mike", Modality.RF)));

        assertThat(registry.servicesFor(Modality.RF))
                .extracting(s -> s.id().value())
                .as("declaration order, not alphabetical and not hash order")
                .containsExactly("zulu", "alpha", "mike");
    }

    @Test
    void anUnservedModalityRoutesNowhereAndThatIsNotAnError() {
        // A deployment need not run a model for every modality it can sense.
        AiServiceRegistry registry = new AiServiceRegistry(List.of(service("alpha", Modality.RF)));

        assertThat(registry.servicesFor(Modality.ACOUSTIC)).isEmpty();
    }

    @Test
    void anEmptyRegistryIsValid() {
        AiServiceRegistry registry = new AiServiceRegistry(List.of());

        assertThat(registry.isEmpty()).isTrue();
        assertThat(registry.servicesFor(Modality.RF)).isEmpty();
        assertThat(registry.all()).isEmpty();
    }

    @Test
    void aServiceIsFoundByItsId() {
        AiServiceRegistry registry = new AiServiceRegistry(List.of(service("alpha", Modality.RF)));

        assertThat(registry.find(new ServiceId("alpha"))).isPresent();
        assertThat(registry.find(new ServiceId("nonexistent"))).isEmpty();
    }

    @Test
    void aDuplicateServiceIdIsRejected() {
        assertThatThrownBy(() -> new AiServiceRegistry(List.of(
                service("alpha", Modality.RF), service("alpha", Modality.RADAR))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate");
    }

    @Test
    void aNewServiceIsPurelyAnAddedEntry() {
        // The guarantee of section 6.1, stated as a test: registering an additional model requires
        // no new type, no subclass, and no change to any existing entry.
        AiServiceRegistry before = new AiServiceRegistry(List.of(service("alpha", Modality.RF)));
        AiServiceRegistry after = new AiServiceRegistry(List.of(
                service("alpha", Modality.RF),
                service("a-model-nobody-has-written-yet", Modality.ACOUSTIC, Modality.GNSS)));

        assertThat(before.servicesFor(Modality.ACOUSTIC)).isEmpty();
        assertThat(after.servicesFor(Modality.ACOUSTIC)).hasSize(1);
        assertThat(after.servicesFor(Modality.RF)).hasSize(1);
    }
}
