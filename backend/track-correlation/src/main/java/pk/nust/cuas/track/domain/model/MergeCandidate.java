package pk.nust.cuas.track.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * A pair of tracks that has looked like one object, and for how many sweeps in a row
 * (ARCHITECTURE.md section 4.1.5).
 *
 * <p>The evidence behind the consecutive-sweep requirement. A single sweep finding two tracks inside
 * the merge gate is a coincidence — two drones in close formation produce exactly that, and merging
 * them would make one of two real threats vanish from the operator's screen. Requiring the pair to
 * stay together across several sweeps is what separates one object seen twice from two objects
 * flying together.
 *
 * <p>The pair is stored with the lower id first so that {@code (a, b)} and {@code (b, a)} are the
 * same row. Without that, a pair could accumulate evidence twice under two orderings and merge on
 * half the intended evidence.
 *
 * @param lowerTrackId      the smaller of the two track ids
 * @param higherTrackId     the larger of the two
 * @param consecutiveSweeps how many sweeps in a row the pair has been inside the gate
 * @param firstObservedAt   when the pair first fell inside the gate
 * @param lastObservedAt    the most recent sweep that found it there
 */
public record MergeCandidate(
        UUID lowerTrackId,
        UUID higherTrackId,
        int consecutiveSweeps,
        Instant firstObservedAt,
        Instant lastObservedAt) {

    public MergeCandidate {
        if (lowerTrackId == null || higherTrackId == null) {
            throw new IllegalArgumentException("both track ids are required");
        }
        if (lowerTrackId.compareTo(higherTrackId) >= 0) {
            throw new IllegalArgumentException(
                    "the pair must be ordered, lower first: " + lowerTrackId + ", " + higherTrackId);
        }
        if (consecutiveSweeps < 1) {
            throw new IllegalArgumentException("a candidate has been seen at least once");
        }
    }

    /**
     * Opens a candidate on the sweep that first found the pair inside the gate.
     *
     * @param a  one track id
     * @param b  the other; order does not matter, it is normalised here
     * @param at the sweep time
     * @return the new candidate with one sweep of evidence
     */
    public static MergeCandidate first(UUID a, UUID b, Instant at) {
        UUID lower = a.compareTo(b) <= 0 ? a : b;
        UUID higher = a.compareTo(b) <= 0 ? b : a;
        return new MergeCandidate(lower, higher, 1, at, at);
    }

    /**
     * @param at the sweep time
     * @return this candidate with one more sweep of evidence
     */
    public MergeCandidate observedAgain(Instant at) {
        return new MergeCandidate(lowerTrackId, higherTrackId, consecutiveSweeps + 1, firstObservedAt, at);
    }
}
