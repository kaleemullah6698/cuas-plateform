package pk.nust.cuas.orchestrator.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import pk.nust.cuas.orchestrator.domain.model.AiServiceDescriptor;
import pk.nust.cuas.orchestrator.domain.model.Modality;
import pk.nust.cuas.orchestrator.domain.model.ProviderMode;
import pk.nust.cuas.orchestrator.domain.model.ServiceId;

class ModalityRoutingPolicyTest {

    private static AiServiceDescriptor service(String id, Modality... modalities) {
        return AiServiceDescriptor.of(new ServiceId(id), Set.of(modalities), ProviderMode.MOCK,
                Optional.empty(), Duration.ofMillis(100), Optional.empty(), 32);
    }

    private static ModalityRoutingPolicy policyOf(AiServiceDescriptor... services) {
        return new ModalityRoutingPolicy(new AiServiceRegistry(List.of(services)));
    }

    @Test
    void aSingleModalityRequestReachesTheServicesForThatModality() {
        ModalityRoutingPolicy policy = policyOf(
                service("alpha", Modality.RF), service("beta", Modality.EO_IR));

        assertThat(policy.route(List.of(Modality.RF)))
                .extracting(s -> s.id().value())
                .containsExactly("alpha");
    }

    @Test
    void aMultiModalRequestReachesTheUnionOfServices() {
        ModalityRoutingPolicy policy = policyOf(
                service("alpha", Modality.RF),
                service("beta", Modality.EO_IR),
                service("gamma", Modality.ACOUSTIC));

        assertThat(policy.route(List.of(Modality.RF, Modality.EO_IR)))
                .extracting(s -> s.id().value())
                .containsExactly("alpha", "beta");
    }

    @Test
    void aMultiModalServiceIsInvokedOnceEvenWhenSeveralOfItsModalitiesArePresent() {
        // Otherwise a fusion service would be called twice for one track: double the GPU load, and
        // two competing results for the same object.
        ModalityRoutingPolicy policy = policyOf(service("fusion", Modality.RADAR, Modality.RF));

        assertThat(policy.route(List.of(Modality.RADAR, Modality.RF)))
                .extracting(s -> s.id().value())
                .containsExactly("fusion");
    }

    @Test
    void aRequestWhoseModalitiesNothingAcceptsRoutesNowhere() {
        ModalityRoutingPolicy policy = policyOf(service("alpha", Modality.RF));

        assertThat(policy.route(List.of(Modality.GNSS))).isEmpty();
    }

    @Test
    void noModalitiesRoutesNowhere() {
        ModalityRoutingPolicy policy = policyOf(service("alpha", Modality.RF));

        assertThat(policy.route(List.of())).isEmpty();
    }

    @Test
    void anEmptyRegistryRoutesNowhereRatherThanFailing() {
        assertThat(policyOf().route(List.of(Modality.RF))).isEmpty();
    }
}
