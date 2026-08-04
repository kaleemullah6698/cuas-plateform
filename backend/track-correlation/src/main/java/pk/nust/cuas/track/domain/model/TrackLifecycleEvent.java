package pk.nust.cuas.track.domain.model;

import java.time.Instant;
import java.util.Optional;

/**
 * A record of a track changing status (ARCHITECTURE.md section 4.1.5, {@code track_lifecycle_events}).
 *
 * <p>Creation is a transition from nothing, so {@code from} is empty. Every other event carries both
 * ends. The reason is a short, human-readable explanation for the audit of a track's life. These
 * are immutable history — the database rejects updates and deletes on the table.
 *
 * @param from       the previous status, empty on creation
 * @param to         the new status
 * @param reason     why the transition happened
 * @param occurredAt when it happened (the triggering detection's ingest time)
 */
public record TrackLifecycleEvent(
        Optional<TrackStatus> from,
        TrackStatus to,
        String reason,
        Instant occurredAt) {

    public TrackLifecycleEvent {
        if (from == null || to == null || occurredAt == null) {
            throw new IllegalArgumentException("from, to, and occurredAt are required");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason is required");
        }
    }

    /** The event recording a track's creation. */
    public static TrackLifecycleEvent creation(TrackStatus initial, Instant at) {
        return new TrackLifecycleEvent(Optional.empty(), initial, "track created", at);
    }

    /** An event recording a transition between two statuses. */
    public static TrackLifecycleEvent transition(TrackStatus from, TrackStatus to, String reason,
                                                 Instant at) {
        return new TrackLifecycleEvent(Optional.of(from), to, reason, at);
    }
}
