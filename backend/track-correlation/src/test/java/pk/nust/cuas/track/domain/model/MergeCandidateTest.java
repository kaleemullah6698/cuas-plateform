package pk.nust.cuas.track.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MergeCandidateTest {

    private static final Instant T = Instant.parse("2026-08-04T12:00:00Z");
    private static final UUID LOWER = UUID.fromString("01900000-0000-7000-8000-000000000001");
    private static final UUID HIGHER = UUID.fromString("01900000-0000-7000-8000-000000000002");

    @Test
    void aPairIsNormalisedToLowerIdFirstWhicheverOrderItIsGivenIn() {
        // Otherwise a pair could accumulate evidence twice under two orderings and merge on half
        // the intended evidence.
        MergeCandidate forwards = MergeCandidate.first(LOWER, HIGHER, T);
        MergeCandidate backwards = MergeCandidate.first(HIGHER, LOWER, T);

        assertThat(forwards.lowerTrackId()).isEqualTo(LOWER);
        assertThat(forwards.higherTrackId()).isEqualTo(HIGHER);
        assertThat(backwards).isEqualTo(forwards);
    }

    @Test
    void aNewCandidateHasOneSweepOfEvidence() {
        MergeCandidate candidate = MergeCandidate.first(LOWER, HIGHER, T);

        assertThat(candidate.consecutiveSweeps()).isEqualTo(1);
        assertThat(candidate.firstObservedAt()).isEqualTo(T);
        assertThat(candidate.lastObservedAt()).isEqualTo(T);
    }

    @Test
    void observingAgainAdvancesTheCountAndKeepsTheFirstSighting() {
        MergeCandidate twice = MergeCandidate.first(LOWER, HIGHER, T).observedAgain(T.plusSeconds(5));

        assertThat(twice.consecutiveSweeps()).isEqualTo(2);
        assertThat(twice.firstObservedAt()).isEqualTo(T);
        assertThat(twice.lastObservedAt()).isEqualTo(T.plusSeconds(5));
    }

    @Test
    void anUnorderedOrSelfPairedCandidateIsRejected() {
        assertThatThrownBy(() -> new MergeCandidate(HIGHER, LOWER, 1, T, T))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MergeCandidate(LOWER, LOWER, 1, T, T))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
