package pk.nust.cuas.track.domain.port;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import pk.nust.cuas.track.domain.model.DetectionAssociation;
import pk.nust.cuas.track.domain.model.MergeCandidate;
import pk.nust.cuas.track.domain.model.SectorId;
import pk.nust.cuas.track.domain.model.Track;
import pk.nust.cuas.track.domain.model.TrackLifecycleEvent;
import pk.nust.cuas.track.domain.model.TrackState;
import pk.nust.cuas.track.domain.model.TrackStatus;

/**
 * Persistence for the track aggregate and its history (ARCHITECTURE.md section 4.1.5, {@code
 * trackdb}). Declared in the domain as a port; the JPA implementation is an outward adapter.
 *
 * <p>The write methods each persist the aggregate row and append the matching history as one unit:
 * {@link #create} and {@link #update} record an associating detection; {@link #coast} records a
 * predicted state with no detection; {@link #lose} records only the terminal transition. So a
 * track's row and the history explaining it never disagree.
 */
public interface TrackRepository {

    /**
     * The open (non-lost) tracks in a sector whose last detection is no older than {@code notBefore}
     * — the association candidates for a detection in that sector.
     *
     * @param sector    the sector to search
     * @param notBefore the earliest last-detection time to include
     * @return the candidate tracks
     */
    List<Track> findOpenInSector(SectorId sector, Instant notBefore);

    /**
     * The open (non-lost) tracks whose last detection is older than {@code threshold} — the tracks
     * the timeout sweep must consider for coasting or loss.
     *
     * @param threshold the last-detection time below which a track may be due a transition
     * @return the stale open tracks
     */
    List<Track> findOpenStaleBefore(Instant threshold);

    /**
     * @param detectionId a detection id
     * @return whether that detection has already been associated to a track — the idempotency guard
     */
    boolean detectionAlreadyAssociated(String detectionId);

    /** Persists a newly opened track: its row, first state, creating association, and creation event. */
    void create(Track track, DetectionAssociation association, TrackLifecycleEvent creation);

    /** Persists an association to an existing track: the advanced row, new state, association, event. */
    void update(Track track, DetectionAssociation association, Optional<TrackLifecycleEvent> lifecycleEvent);

    /** Persists a coasting transition: the predicted row and state, and the coasting event. */
    void coast(Track track, TrackLifecycleEvent lifecycleEvent);

    /** Persists a lost transition: the terminal row status and the lost event; the state is unchanged. */
    void lose(Track track, TrackLifecycleEvent lifecycleEvent);

    /**
     * Persists a merge: the younger track's terminal row, its link to the survivor, and the event.
     *
     * <p>The survivor is untouched — it has been fed continuously and already holds the better
     * estimate, so a merge changes only the track being absorbed.
     *
     * @param merged         the younger track, now {@code MERGED}
     * @param lifecycleEvent the transition into {@code MERGED}
     */
    void merge(Track merged, TrackLifecycleEvent lifecycleEvent);

    /**
     * The confirmed tracks that could still be merged, across every sector.
     *
     * <p>Only {@code CONFIRMED} tracks, because {@link pk.nust.cuas.track.domain.service.TrackMergePolicy}
     * will reject anything else; filtering here keeps the merge sweep from loading tracks it cannot use.
     *
     * @return the merge-eligible tracks
     */
    List<Track> findConfirmed();

    /**
     * The distinct sensors that have fed a track — what makes a merge a <em>cross-sensor</em> one.
     *
     * @param trackId the track
     * @return the sensor ids, empty when the track has no associations
     */
    Set<String> contributingSensors(UUID trackId);

    /**
     * @param id a track id
     * @return the track, or empty if none exists
     */
    Optional<Track> findById(UUID id);

    /**
     * Lists tracks for the read API, filtered by sector and status when given, newest last-seen first.
     */
    List<Track> search(Optional<SectorId> sector, Optional<TrackStatus> status, int limit);

    /**
     * @param trackId a track id
     * @param limit   the maximum number of states to return
     * @return the track's observed states, newest first
     */
    List<TrackState> history(UUID trackId, int limit);

    /**
     * @param a one track id
     * @param b the other; order does not matter
     * @return the accumulated merge evidence for the pair, or empty when they have not been seen
     *     together on a previous sweep
     */
    Optional<MergeCandidate> findMergeCandidate(UUID a, UUID b);

    /** Inserts or advances a pair's merge evidence. */
    void saveMergeCandidate(MergeCandidate candidate);

    /**
     * Discards a pair's accumulated evidence — the pair drifted back outside the gate, or one of them
     * has merged. The requirement is <em>consecutive</em> sweeps, so a break resets the count to zero
     * rather than pausing it.
     *
     * @param a one track id
     * @param b the other; order does not matter
     */
    void deleteMergeCandidate(UUID a, UUID b);

    /**
     * Discards every candidacy a track is part of. Used when a track leaves {@code CONFIRMED} for any
     * reason, so stale evidence cannot make a later, unrelated pairing merge early.
     *
     * @param trackId the track whose candidacies are cleared
     */
    void deleteMergeCandidatesFor(UUID trackId);
}
