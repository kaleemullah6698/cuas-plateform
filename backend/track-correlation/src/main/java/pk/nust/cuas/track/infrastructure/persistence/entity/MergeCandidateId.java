package pk.nust.cuas.track.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * The composite key of {@code track_merge_candidates}: an ordered pair of track ids, lower first.
 *
 * <p>The ordering is a database {@code CHECK} as well as a convention here, so a pair has exactly one
 * row regardless of which order a caller supplies it in.
 */
@Embeddable
public class MergeCandidateId implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(name = "lower_track_id", nullable = false)
    private UUID lowerTrackId;

    @Column(name = "higher_track_id", nullable = false)
    private UUID higherTrackId;

    protected MergeCandidateId() {
    }

    public MergeCandidateId(UUID lowerTrackId, UUID higherTrackId) {
        this.lowerTrackId = lowerTrackId;
        this.higherTrackId = higherTrackId;
    }

    /** Builds the key for two ids in any order, normalising to lower-first. */
    public static MergeCandidateId of(UUID a, UUID b) {
        return a.compareTo(b) <= 0 ? new MergeCandidateId(a, b) : new MergeCandidateId(b, a);
    }

    public UUID getLowerTrackId() {
        return lowerTrackId;
    }

    public UUID getHigherTrackId() {
        return higherTrackId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof MergeCandidateId id
                && Objects.equals(lowerTrackId, id.lowerTrackId)
                && Objects.equals(higherTrackId, id.higherTrackId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(lowerTrackId, higherTrackId);
    }
}
