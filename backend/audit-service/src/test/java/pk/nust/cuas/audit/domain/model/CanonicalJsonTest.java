package pk.nust.cuas.audit.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Canonical JSON is what makes the record hash reproducible, so these tests pin the exact output
 * rather than merely asserting it parses.
 */
class CanonicalJsonTest {

    @Test
    void emptyAndNullBothProduceEmptyObject() {
        assertThat(CanonicalJson.of(null)).isEqualTo("{}");
        assertThat(CanonicalJson.of(Map.of())).isEqualTo("{}");
    }

    @Test
    void keysAreEmittedInLexicographicOrder() {
        Map<String, String> insertionOrdered = new LinkedHashMap<>();
        insertionOrdered.put("zebra", "1");
        insertionOrdered.put("alpha", "2");
        insertionOrdered.put("middle", "3");

        assertThat(CanonicalJson.of(insertionOrdered))
                .isEqualTo("{\"alpha\":\"2\",\"middle\":\"3\",\"zebra\":\"1\"}");
    }

    @Test
    void differentInsertionOrdersProduceIdenticalOutput() {
        Map<String, String> first = new LinkedHashMap<>();
        first.put("a", "1");
        first.put("b", "2");
        Map<String, String> second = new LinkedHashMap<>();
        second.put("b", "2");
        second.put("a", "1");

        assertThat(CanonicalJson.of(first)).isEqualTo(CanonicalJson.of(second));
    }

    @Test
    void noInsignificantWhitespaceIsEmitted() {
        assertThat(CanonicalJson.of(Map.of("k", "v"))).isEqualTo("{\"k\":\"v\"}");
    }

    @Test
    void quotesAndBackslashesAreEscaped() {
        assertThat(CanonicalJson.of(Map.of("quote", "a\"b")))
                .isEqualTo("{\"quote\":\"a\\\"b\"}");
        assertThat(CanonicalJson.of(Map.of("slash", "a\\b")))
                .isEqualTo("{\"slash\":\"a\\\\b\"}");
    }

    @Test
    void controlCharactersAreEscaped() {
        assertThat(CanonicalJson.of(Map.of("nl", "a\nb"))).isEqualTo("{\"nl\":\"a\\nb\"}");
        assertThat(CanonicalJson.of(Map.of("tab", "a\tb"))).isEqualTo("{\"tab\":\"a\\tb\"}");
        assertThat(CanonicalJson.of(Map.of("unit", "ab")))
                .isEqualTo("{\"unit\":\"a\\u0001b\"}");
    }

    @Test
    void escapingPreventsKeyValueAmbiguity() {
        // Without escaping, a value containing a quote could close its own literal and forge
        // additional structure, changing the hashed bytes for two different inputs.
        String withQuoteInValue = CanonicalJson.of(Map.of("a", "\",\"b\":\"x"));
        String twoRealKeys = CanonicalJson.of(Map.of("a", "", "b", "x"));

        assertThat(withQuoteInValue).isNotEqualTo(twoRealKeys);
    }
}
