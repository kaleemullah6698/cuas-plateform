package pk.nust.cuas.track.domain.model;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import pk.nust.cuas.track.domain.service.TrackConfirmationPolicy;

/**
 * A track: the trajectory of one airborne object, built by associating successive detections
 * (ARCHITECTURE.md section 4.1.5). The aggregate root of this service.
 *
 * <p>Immutable (CLAUDE.md section 3): associating a detection returns a new {@code Track} and the
 * lifecycle event, if any, that the association produced — the object is never mutated in place, so
 * the before and after states are both available to the caller that persists and publishes them.
 * The current {@link #state()} is the latest observed state; {@link #lastSeen()} is its time.
 *
 * <p>The foundation carries the latest detection's position and velocity as the track's state and
 * confirms on a detection count. Kalman-filtered estimation, and the coasting/lost transitions,
 * are M5.2.
 */
public final class Track {

    private final UUID id;
    private final SectorId sector;
    private final TrackStatus status;
    private final TrackState state;
    private final int detectionCount;
    private final Instant firstSeen;

    private Track(UUID id, SectorId sector, TrackStatus status, TrackState state,
                  int detectionCount, Instant firstSeen) {
        this.id = id;
        this.sector = sector;
        this.status = status;
        this.state = state;
        this.detectionCount = detectionCount;
        this.firstSeen = firstSeen;
    }

    /**
     * Opens a new tentative track from the first detection to fall outside every existing track's gate.
     *
     * @param id      the new track's identity (a UUIDv7)
     * @param sector  the sector the initial position falls in
     * @param initial the state of the detection that created the track
     * @return the new track, {@code TENTATIVE} with one detection
     */
    public static Track open(UUID id, SectorId sector, TrackState initial) {
        return new Track(id, sector, TrackStatus.TENTATIVE, initial, 1, initial.observedAt());
    }

    /**
     * Reconstitutes a track from storage. Used by the repository; not part of the domain's own
     * behaviour.
     */
    public static Track reconstitute(UUID id, SectorId sector, TrackStatus status, TrackState state,
                                     int detectionCount, Instant firstSeen) {
        return new Track(id, sector, status, state, detectionCount, firstSeen);
    }

    /**
     * Associates a detection to this track, advancing its state and, if the confirmation rule is now
     * met, its status.
     *
     * @param incoming     the state of the associating detection
     * @param confirmation the rule that decides tentative → confirmed
     * @return the updated track and the lifecycle event the association produced, if any
     */
    public AssociationOutcome associate(TrackState incoming, TrackConfirmationPolicy confirmation) {
        int newCount = detectionCount + 1;
        TrackStatus newStatus = status;
        Optional<TrackLifecycleEvent> event = Optional.empty();
        if (status == TrackStatus.TENTATIVE && confirmation.shouldConfirm(newCount)) {
            newStatus = TrackStatus.CONFIRMED;
            event = Optional.of(TrackLifecycleEvent.transition(TrackStatus.TENTATIVE, TrackStatus.CONFIRMED,
                    "confirmed after " + newCount + " associated detections", incoming.observedAt()));
        }
        Track updated = new Track(id, sector, newStatus, incoming, newCount, firstSeen);
        return new AssociationOutcome(updated, event);
    }

    public UUID id() {
        return id;
    }

    public SectorId sector() {
        return sector;
    }

    public TrackStatus status() {
        return status;
    }

    public TrackState state() {
        return state;
    }

    public int detectionCount() {
        return detectionCount;
    }

    public Instant firstSeen() {
        return firstSeen;
    }

    /** The time of the latest observed state — a track's last-seen time. */
    public Instant lastSeen() {
        return state.observedAt();
    }

    /**
     * The result of {@link #associate}: the advanced track and the lifecycle event it produced.
     *
     * @param track          the track after association
     * @param lifecycleEvent the transition it caused, empty when the status did not change
     */
    public record AssociationOutcome(Track track, Optional<TrackLifecycleEvent> lifecycleEvent) {
    }
}
