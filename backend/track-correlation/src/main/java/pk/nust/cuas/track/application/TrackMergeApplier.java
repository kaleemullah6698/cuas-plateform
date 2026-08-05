package pk.nust.cuas.track.application;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pk.nust.cuas.track.application.port.TrackEventPublisher;
import pk.nust.cuas.track.domain.model.MergeCandidate;
import pk.nust.cuas.track.domain.model.Track;
import pk.nust.cuas.track.domain.model.TrackStatus;
import pk.nust.cuas.track.domain.port.HotTrackStateStore;
import pk.nust.cuas.track.domain.port.TrackRepository;
import pk.nust.cuas.track.domain.service.TrackMergePolicy;

/**
 * Advances one pair's merge evidence, and performs the merge when it is enough (ARCHITECTURE.md
 * section 4.1.5).
 *
 * <p>Separate from the sweep so each pair commits independently: a pair whose tracks were changed by
 * a concurrent detection fails only its own transaction, and the sweep moves on. Both tracks are
 * re-read here rather than trusted from the sweep's snapshot, so the decision is made against their
 * current state — between the snapshot and this transaction a detection may have revived, coasted,
 * or already merged one of them.
 *
 * <p>Persist before publish, in the transaction, so a publish failure rolls the merge back rather
 * than announcing a merge that did not happen.
 */
@Service
public class TrackMergeApplier {

    private static final Logger log = LoggerFactory.getLogger(TrackMergeApplier.class);

    private final TrackRepository tracks;
    private final HotTrackStateStore hotState;
    private final TrackMergePolicy mergePolicy;
    private final TrackEventPublisher publisher;

    public TrackMergeApplier(TrackRepository tracks, HotTrackStateStore hotState,
                             TrackMergePolicy mergePolicy, TrackEventPublisher publisher) {
        this.tracks = tracks;
        this.hotState = hotState;
        this.mergePolicy = mergePolicy;
        this.publisher = publisher;
    }

    /**
     * Records one more sweep of evidence for a pair and merges them if that reaches the threshold.
     *
     * @param aId one track
     * @param bId the other
     * @param now the sweep time
     * @return whether the pair was merged
     */
    @Transactional
    public boolean advanceAndMaybeMerge(UUID aId, UUID bId, Instant now) {
        Optional<Track> maybeA = tracks.findById(aId);
        Optional<Track> maybeB = tracks.findById(bId);
        if (maybeA.isEmpty() || maybeB.isEmpty()) {
            return false;
        }
        Track a = maybeA.get();
        Track b = maybeB.get();
        // Re-checked against current state: the sweep's snapshot may be stale by now.
        if (a.status() != TrackStatus.CONFIRMED || b.status() != TrackStatus.CONFIRMED) {
            tracks.deleteMergeCandidate(aId, bId);
            return false;
        }

        MergeCandidate advanced = tracks.findMergeCandidate(aId, bId)
                .map(existing -> existing.observedAgain(now))
                .orElseGet(() -> MergeCandidate.first(aId, bId, now));

        if (!mergePolicy.shouldMerge(advanced.consecutiveSweeps())) {
            tracks.saveMergeCandidate(advanced);
            log.debug("Merge candidacy {}+{} at {}/{} sweeps", aId, bId,
                    advanced.consecutiveSweeps(), mergePolicy.requiredConsecutiveSweeps());
            return false;
        }

        Track survivor = mergePolicy.survivorOf(a, b);
        Track absorbed = survivor.id().equals(a.id()) ? b : a;
        Track.Transition transition = absorbed.mergeInto(survivor, now);

        tracks.merge(transition.track(), transition.lifecycleEvent().orElseThrow());
        // Terminal: the survivor is the only one of the pair that may still take detections.
        hotState.evict(absorbed.sector(), absorbed.id());
        publisher.publishMerged(transition.track());
        log.info("Merged track {} into {} after {} consecutive sweeps inside the gate",
                absorbed.id(), survivor.id(), advanced.consecutiveSweeps());
        return true;
    }

    /**
     * Discards a pair's accumulated evidence — they are no longer inside the gate. The requirement is
     * <em>consecutive</em> sweeps, so a break resets rather than pauses.
     *
     * @param aId one track
     * @param bId the other
     */
    @Transactional
    public void discard(UUID aId, UUID bId) {
        tracks.deleteMergeCandidate(aId, bId);
    }
}
