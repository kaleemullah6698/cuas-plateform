package pk.nust.cuas.orchestrator.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import pk.nust.cuas.orchestrator.domain.model.AiServiceDescriptor;
import pk.nust.cuas.orchestrator.domain.model.Modality;
import pk.nust.cuas.orchestrator.domain.model.ProviderMode;
import pk.nust.cuas.orchestrator.domain.model.ServiceId;

class CanaryPolicyTest {

    private final CanaryPolicy policy = new CanaryPolicy();

    private static AiServiceDescriptor service(String id, Optional<String> baseline,
                                               Optional<String> canary, int percentage) {
        return new AiServiceDescriptor(new ServiceId(id), Set.of(Modality.RF), ProviderMode.MOCK,
                Optional.empty(), Duration.ofMillis(100), baseline, 32, canary, percentage);
    }

    private long canaryCountOver(AiServiceDescriptor service, int tracks) {
        long count = 0;
        for (int i = 0; i < tracks; i++) {
            if (policy.isCanary(service, "01900000-0000-7000-8000-%012x".formatted(i))) {
                count++;
            }
        }
        return count;
    }

    @Test
    void withNoCanaryConfiguredTheBaselineIsAlwaysUsed() {
        AiServiceDescriptor plain = service("s", Optional.of("1.2.0"), Optional.empty(), 0);

        assertThat(policy.versionFor(plain, "any-track")).contains("1.2.0");
        assertThat(policy.isCanary(plain, "any-track")).isFalse();
    }

    @Test
    void withNoVersionsAtAllTheServiceDefaultIsLeftToDecide() {
        AiServiceDescriptor plain = service("s", Optional.empty(), Optional.empty(), 0);

        assertThat(policy.versionFor(plain, "any-track")).isEmpty();
    }

    @Test
    void aTrackKeepsItsAssignmentForLife() {
        // Splitting per call would mean one drone's successive classifications came from two models,
        // so a change in its threat level could be the model changing rather than the situation —
        // and an operator would have no way to tell which.
        AiServiceDescriptor canaried = service("s", Optional.of("1.2.0"), Optional.of("1.3.0"), 50);

        boolean first = policy.isCanary(canaried, "stable-track");
        for (int i = 0; i < 100; i++) {
            assertThat(policy.isCanary(canaried, "stable-track")).isEqualTo(first);
            assertThat(policy.versionFor(canaried, "stable-track"))
                    .contains(first ? "1.3.0" : "1.2.0");
        }
    }

    @Test
    void theConfiguredShareIsObservedAcrossManyTracks() {
        AiServiceDescriptor tenPercent = service("s", Optional.of("1.2.0"), Optional.of("1.3.0"), 10);

        long onCanary = canaryCountOver(tenPercent, 4000);

        assertThat(onCanary / 4000.0d).isBetween(0.07d, 0.13d);
    }

    @Test
    void ninetyTenSplitsAsSpecified() {
        // Section 6.6's worked example: 90% v1.2.0 / 10% v1.3.0.
        AiServiceDescriptor split = service("s", Optional.of("1.2.0"), Optional.of("1.3.0"), 10);
        long baseline = 0;
        long canary = 0;
        for (int i = 0; i < 2000; i++) {
            String trackId = "01900000-0000-7000-8000-%012x".formatted(i);
            if (policy.versionFor(split, trackId).orElseThrow().equals("1.3.0")) {
                canary++;
            } else {
                baseline++;
            }
        }

        assertThat(baseline + canary).isEqualTo(2000);
        assertThat(canary / 2000.0d).isBetween(0.07d, 0.13d);
    }

    @Test
    void zeroPercentSendsNothingToTheCanary() {
        AiServiceDescriptor none = service("s", Optional.of("1.2.0"), Optional.of("1.3.0"), 0);

        assertThat(canaryCountOver(none, 500)).isZero();
        assertThat(policy.versionFor(none, "t")).contains("1.2.0");
    }

    @Test
    void oneHundredPercentSendsEverythingToTheCanary() {
        AiServiceDescriptor all = service("s", Optional.of("1.2.0"), Optional.of("1.3.0"), 100);

        assertThat(canaryCountOver(all, 500)).isEqualTo(500);
        assertThat(policy.versionFor(all, "t")).contains("1.3.0");
    }

    @Test
    void aTrackIsNotSystematicallyOnEveryServicesCanary() {
        // The service id is part of the seed. Without it a track on one canary would be on all of
        // them, correlating the experiments so one model's regression looks like another's.
        AiServiceDescriptor first = service("service-a", Optional.of("1.0.0"), Optional.of("2.0.0"), 50);
        AiServiceDescriptor second = service("service-b", Optional.of("1.0.0"), Optional.of("2.0.0"), 50);

        int differing = 0;
        for (int i = 0; i < 500; i++) {
            String trackId = "track-" + i;
            if (policy.isCanary(first, trackId) != policy.isCanary(second, trackId)) {
                differing++;
            }
        }

        assertThat(differing)
                .as("assignments should be independent across services, not identical")
                .isBetween(200, 300);
    }

    @Test
    void aCanaryPercentageWithoutAVersionIsRejectedAtConstruction() {
        // It would silently route traffic nowhere new.
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        service("s", Optional.of("1.2.0"), Optional.empty(), 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("canary version");
    }

    @Test
    void aPercentageOutsideZeroToOneHundredIsRejected() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        service("s", Optional.of("1.2.0"), Optional.of("1.3.0"), 101))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
