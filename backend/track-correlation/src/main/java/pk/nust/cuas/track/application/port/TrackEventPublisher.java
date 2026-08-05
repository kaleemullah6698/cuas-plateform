package pk.nust.cuas.track.application.port;

import pk.nust.cuas.track.domain.model.Track;

/**
 * Publishes track lifecycle events to Kafka (ARCHITECTURE.md sections 4.1.5, 5.2).
 *
 * <p>An output port owned by the application layer and implemented by the Kafka adapter, which maps
 * the domain {@link Track} to the {@code track.v1.Track} contract. A track is published on creation
 * and on every association; both are keyed by track id so a track's events stay ordered on one
 * partition.
 */
public interface TrackEventPublisher {

    /**
     * Publishes a newly created track to {@code track.created.v1}.
     *
     * @param track the created track
     */
    void publishCreated(Track track);

    /**
     * Publishes an updated track to {@code track.updated.v1}. Used for both a detection-driven update
     * and a coasting prediction — the topic carries the track's new estimated state either way.
     *
     * @param track the updated track
     */
    void publishUpdated(Track track);

    /**
     * Publishes a lost track to {@code track.lost.v1} — the terminal lifecycle event.
     *
     * @param track the lost track
     */
    void publishLost(Track track);
}
