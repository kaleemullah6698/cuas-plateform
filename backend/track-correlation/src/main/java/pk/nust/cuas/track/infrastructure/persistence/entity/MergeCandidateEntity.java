package pk.nust.cuas.track.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * JPA mapping for {@code track_merge_candidates} — a pair of tracks and how many consecutive sweeps
 * they have looked like one object. A separate type from the domain {@code MergeCandidate}, mapped
 * explicitly (CLAUDE.md section 2).
 *
 * <p>The key is the ordered pair, so {@code (a,b)} and {@code (b,a)} are necessarily the same row and
 * a pair cannot accumulate evidence twice under two orderings.
 */
@Entity
@Table(name = "track_merge_candidates")
public class MergeCandidateEntity {

    @EmbeddedId
    private MergeCandidateId id;

    @Column(name = "consecutive_sweeps", nullable = false)
    private int consecutiveSweeps;

    @Column(name = "first_observed_at", nullable = false)
    private Instant firstObservedAt;

    @Column(name = "last_observed_at", nullable = false)
    private Instant lastObservedAt;

    protected MergeCandidateEntity() {
    }

    public MergeCandidateEntity(MergeCandidateId id, int consecutiveSweeps,
                                Instant firstObservedAt, Instant lastObservedAt) {
        this.id = id;
        this.consecutiveSweeps = consecutiveSweeps;
        this.firstObservedAt = firstObservedAt;
        this.lastObservedAt = lastObservedAt;
    }

    public MergeCandidateId getId() {
        return id;
    }

    public int getConsecutiveSweeps() {
        return consecutiveSweeps;
    }

    public Instant getFirstObservedAt() {
        return firstObservedAt;
    }

    public Instant getLastObservedAt() {
        return lastObservedAt;
    }
}
