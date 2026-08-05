package pk.nust.cuas.orchestrator.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class RequestContentHashTest {

    private static String hash(List<String> fingerprints) {
        return RequestContentHash.of(fingerprints, "", 0.0f, false, false);
    }

    @Test
    void identicalContentHashesIdentically() {
        assertThat(hash(List.of("d1", "d2"))).isEqualTo(hash(List.of("d1", "d2")));
    }

    @Test
    void differentDetectionsHashDifferently() {
        assertThat(hash(List.of("d1"))).isNotEqualTo(hash(List.of("d2")));
    }

    @Test
    void detectionOrderIsPartOfTheContent() {
        // Two orderings are two different requests as far as a provider is concerned.
        assertThat(hash(List.of("d1", "d2"))).isNotEqualTo(hash(List.of("d2", "d1")));
    }

    @Test
    void fingerprintsCannotRunTogetherToCollide() {
        // Length-prefixed, so ["ab","c"] and ["a","bc"] are distinguishable. Without that, two
        // different observations would share a cache entry and one track would be served another's
        // classification.
        assertThat(hash(List.of("ab", "c"))).isNotEqualTo(hash(List.of("a", "bc")));
    }

    @Test
    void aPinnedModelVersionChangesTheKey() {
        // A canary asks a *different model* the same question; sharing a key would serve the
        // incumbent's answer and make the canary invisible.
        assertThat(RequestContentHash.of(List.of("d1"), "1.3.0", 0.0f, false, false))
                .isNotEqualTo(RequestContentHash.of(List.of("d1"), "1.4.0", 0.0f, false, false));
    }

    @Test
    void optionsThatChangeTheAnswerChangeTheKey() {
        List<String> one = List.of("d1");

        assertThat(RequestContentHash.of(one, "", 0.5f, false, false))
                .as("min confidence changes which predictions come back")
                .isNotEqualTo(RequestContentHash.of(one, "", 0.9f, false, false));
        assertThat(RequestContentHash.of(one, "", 0.0f, true, false))
                .as("embeddings requested is a different response shape")
                .isNotEqualTo(RequestContentHash.of(one, "", 0.0f, false, false));
        assertThat(RequestContentHash.of(one, "", 0.0f, false, true))
                .as("attributions requested is a different response shape")
                .isNotEqualTo(RequestContentHash.of(one, "", 0.0f, false, false));
    }

    @Test
    void theHashIsAHexSha256() {
        assertThat(hash(List.of("d1"))).hasSize(64).matches("[0-9a-f]{64}");
    }

    @Test
    void anEmptyDetectionListStillHashes() {
        assertThat(hash(List.of())).hasSize(64);
    }
}
