package pk.nust.cuas.config.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/** The immutable, version-advancing transitions of the {@link Configuration} aggregate. */
class ConfigurationTest {

    private final Instant now = Instant.parse("2026-08-07T12:00:00Z");

    @Test
    void aNewNamespaceOpensAtVersionOne() {
        Configuration config = Configuration.initial("policy", ConfigNamespaceKind.POLICY, "{\"a\":1}",
                "usr-1", "initial", now);

        assertThat(config.version()).isEqualTo(1);
        assertThat(config.deleted()).isFalse();
        assertThat(config.valueJson()).isEqualTo("{\"a\":1}");
    }

    @Test
    void anUpdateAdvancesTheVersion() {
        Configuration updated = Configuration.initial("policy", ConfigNamespaceKind.POLICY, "{}",
                "usr-1", "initial", now).updatedWith("{\"a\":2}", "usr-2", "retune", now);

        assertThat(updated.version()).isEqualTo(2);
        assertThat(updated.valueJson()).isEqualTo("{\"a\":2}");
        assertThat(updated.lastActorId()).contains("usr-2");
    }

    @Test
    void aRollbackRestoresAPastValueAsAHigherVersion() {
        Configuration v1 = Configuration.initial("policy", ConfigNamespaceKind.POLICY, "{\"a\":1}",
                "usr-1", "initial", now);
        Configuration v2 = v1.updatedWith("{\"a\":2}", "usr-2", "retune", now);
        ConfigVersion target = ConfigVersion.of(v1);

        Configuration rolled = v2.restoredFrom(target, "usr-3", "revert the retune", now);

        assertThat(rolled.version()).isEqualTo(3);
        assertThat(rolled.valueJson()).isEqualTo("{\"a\":1}");
    }

    @Test
    void aDeleteRetainsTheLastValueForRecall() {
        Configuration deleted = Configuration.initial("policy", ConfigNamespaceKind.POLICY, "{\"a\":1}",
                "usr-1", "initial", now).deletedBy("usr-2", "decommissioned", now);

        assertThat(deleted.deleted()).isTrue();
        assertThat(deleted.version()).isEqualTo(2);
        assertThat(deleted.valueJson()).isEqualTo("{\"a\":1}");
    }

    @Test
    void aNamespaceAndValueAreRequired() {
        assertThatThrownBy(() -> Configuration.initial("", ConfigNamespaceKind.POLICY, "{}", "u", "j", now))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Configuration.initial("policy", ConfigNamespaceKind.POLICY, "  ",
                "u", "j", now))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
