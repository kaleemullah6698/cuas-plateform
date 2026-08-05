package pk.nust.cuas.track.application.port;

import pk.nust.cuas.track.domain.model.Track;
import pk.nust.cuas.track.domain.model.TrackOutlook;

/**
 * Fans a track change out to the live gRPC subscribers of this instance (ARCHITECTURE.md sections
 * 4.1.5, 4.3).
 *
 * <p>In-process, and correct because of the sector model: an instance owns its sectors exclusively,
 * so every change to a track it owns happens inside it. Feeding the stream from Kafka instead would
 * add a broker round trip to a latency budget (§5.8) that already spends its headroom on inference,
 * and would give each subscriber a consumer group to manage.
 *
 * <p>The stream is a live feed, not a log: a subscriber that connects late has missed what came
 * before, and the topics remain the durable record. That is what makes fan-out safe to do
 * best-effort — a broadcast failure must never affect the correlation transaction, because a
 * subscriber falling behind is not a reason to stop tracking a drone.
 */
public interface TrackStreamBroadcaster {

    /** A lifecycle event, matching the topic the same change is published on. */
    enum EventType {
        CREATED, UPDATED, LOST, MERGED
    }

    /**
     * Offers a change to every subscriber whose filter matches.
     *
     * @param type    which lifecycle event this is
     * @param track   the track as it now stands
     * @param outlook its trajectory and CPA; {@link TrackOutlook#none()} for terminal events
     */
    void broadcast(EventType type, Track track, TrackOutlook outlook);
}
