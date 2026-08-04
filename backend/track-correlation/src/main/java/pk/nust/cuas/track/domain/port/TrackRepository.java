package pk.nust.cuas.track.domain.port;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import pk.nust.cuas.track.domain.model.DetectionAssociation;
import pk.nust.cuas.track.domain.model.SectorId;
import pk.nust.cuas.track.domain.model.Track;
import pk.nust.cuas.track.domain.model.TrackLifecycleEvent;
import pk.nust.cuas.track.domain.model.TrackState;
import pk.nust.cuas.track.domain.model.TrackStatus;

/**
 * Persistence for the track aggregate and its history (ARCHITECTURE.md section 4.1.5, {@code
 * trackdb}). Declared in the domain as a port; the JPA implementation is an outward adapter.
 *
 * <p>{@link #create} and {@link #update} each write the aggregate and append to its history — the
 * new state, the association, and any lifecycle event — as one unit, so a track's row and the
 * history explaining it never disagree.
 */
public interface TrackRepository {

    /**
     * The open tracks in a sector whose last state is no older than {@code notBefore} — the
     * association candidates for a detection in that sector.
     *
     * @param sector    the sector to search
     * @param notBefore the earliest last-seen time to include
     * @return the candidate tracks
     */
    List<Track> findOpenInSector(SectorId sector, Instant notBefore);

    /**
     * @param detectionId a detection id
     * @return whether that detection has already been associated to a track — the idempotency guard
     *         that makes at-least-once redelivery safe
     */
    boolean detectionAlreadyAssociated(String detectionId);

    /**
     * Persists a newly opened track: its row, its first state, the creating association, and the
     * creation lifecycle event.
     */
    void create(Track track, DetectionAssociation association, TrackLifecycleEvent creation);

    /**
     * Persists an association to an existing track: the advanced row, the new state, the association,
     * and the lifecycle event if the status changed.
     */
    void update(Track track, DetectionAssociation association, Optional<TrackLifecycleEvent> lifecycleEvent);

    /**
     * @param id a track id
     * @return the track, or empty if none exists
     */
    Optional<Track> findById(UUID id);

    /**
     * Lists tracks for the read API, filtered by sector and status when given, newest last-seen first.
     *
     * @param sector optional sector filter
     * @param status optional status filter
     * @param limit  the maximum number to return
     * @return the matching tracks
     */
    List<Track> search(Optional<SectorId> sector, Optional<TrackStatus> status, int limit);

    /**
     * @param trackId a track id
     * @param limit   the maximum number of states to return
     * @return the track's observed states, newest first
     */
    List<TrackState> history(UUID trackId, int limit);
}
