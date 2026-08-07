package pk.nust.cuas.config.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** Structural validation and canonicalisation of a configuration value. */
class ConfigCanonicalizerTest {

    private final ConfigCanonicalizer canonicalizer = new ConfigCanonicalizer();

    @Test
    void itSortsObjectKeysSoTheSameValueStoresIdentically() {
        assertThat(canonicalizer.canonicalize("{\"b\":1,\"a\":2}")).isEqualTo("{\"a\":2,\"b\":1}");
    }

    @Test
    void itRejectsMalformedJson() {
        assertThatThrownBy(() -> canonicalizer.canonicalize("{not json"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("valid JSON");
    }

    @Test
    void itRejectsANonObjectValue() {
        assertThatThrownBy(() -> canonicalizer.canonicalize("[1,2,3]"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("JSON object");
        assertThatThrownBy(() -> canonicalizer.canonicalize("42"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void itRejectsABlankValue() {
        assertThatThrownBy(() -> canonicalizer.canonicalize("  "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
