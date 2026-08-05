package pk.nust.cuas.track.application.port;

import pk.nust.cuas.track.domain.model.Track;
import pk.nust.cuas.track.domain.model.TrackOutlook;

/**
 * Publishes track lifecycle events to Kafka (ARCHITECTURE.md sections 4.1.5, 5.2).
 *
 * <p>An output port owned by the application layer and implemented by the Kafka adapter, which maps
 * the domain {@link Track} to the {@code track.v1.Track} contract. A track is published on creation
 * and on every association; both are keyed by track id so a track's events stay ordered on one
 * partition.
 *
 * <p>Every method takes the track's {@link TrackOutlook} alongside it. The outlook is derived, not
 * stored, so the alternative would be for the adapter to compute it — which would put domain
 * arithmetic in infrastructure. Passing it in keeps the adapter a pure mapper and leaves the caller
 * in charge of *when* a prediction is worth making.
 */
public interface TrackEventPublisher {

    /**
     * Publishes a newly created track to {@code track.created.v1}.
     *
     * @param track   the created track
     * @param outlook its predicted trajectory and CPA
     */
    void publishCreated(Track track, TrackOutlook outlook);

    /**
     * Publishes an updated track to {@code track.updated.v1}. Used for both a detection-driven update
     * and a coasting prediction — the topic carries the track's new estimated state either way.
     *
     * @param track   the updated track
     * @param outlook its predicted trajectory and CPA
     */
    void publishUpdated(Track track, TrackOutlook outlook);

    /**
     * Publishes a lost track to {@code track.lost.v1} — the terminal lifecycle event.
     *
     * <p>No outlook: a lost track is not going anywhere, and publishing a trajectory for one would
     * put a moving prediction on screen for a track the system has just given up on.
     *
     * @param track the lost track
     */
    void publishLost(Track track);

    /**
     * Publishes a merged track to {@code track.merged.v1} — the other terminal event.
     *
     * <p>The message carries the absorbed track with {@code merged_into_track_id} set, so a consumer
     * holding the absorbed id can re-key onto the survivor rather than concluding the object
     * disappeared. No outlook, for the same reason as a lost track: the survivor is the one still
     * being predicted.
     *
     * @param merged the absorbed track, which must name its survivor
     */
    void publishMerged(Track merged);
}
