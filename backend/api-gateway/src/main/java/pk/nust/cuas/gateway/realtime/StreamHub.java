package pk.nust.cuas.gateway.realtime;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import pk.nust.cuas.gateway.realtime.message.Payloads.SensorsDelta;
import pk.nust.cuas.gateway.realtime.message.Payloads.SensorsSnapshot;
import pk.nust.cuas.gateway.realtime.message.Payloads.TracksDelta;
import pk.nust.cuas.gateway.realtime.message.Payloads.TracksSnapshot;
import pk.nust.cuas.gateway.realtime.message.ServerMessages;
import pk.nust.cuas.gateway.realtime.view.AlertView;
import pk.nust.cuas.gateway.realtime.view.SensorView;
import pk.nust.cuas.gateway.realtime.view.TrackView;

/**
 * Maintains the gateway's live view of the airspace and fans it out (ARCHITECTURE.md §3.5, §2.5.1).
 *
 * <p><b>The snapshot comes from here, not from a topic.</b> For M9 the gateway builds its current-state
 * view from the {@code track.*}/{@code sensor.health} stream it consumes, and serves that as the
 * {@code SNAPSHOT} — decision D-1 (ROADMAP M9), which avoids needing the never-produced
 * {@code track.state.snapshot.v1}. Track and sensor changes are coalesced latest-wins and flushed as
 * DELTAs on a fixed window (the §2.5.1 4 Hz console budget), so a 20 Hz source cannot flood a client.
 * Alerts bypass coalescing entirely — every alert is pushed the instant it arrives.
 */
@Component
public class StreamHub {

    private final Map<String, TrackView> tracks = new ConcurrentHashMap<>();
    private final Map<String, SensorView> sensors = new ConcurrentHashMap<>();
    private final Map<String, TrackView> pendingTrackChanges = new ConcurrentHashMap<>();
    private final Set<String> pendingTrackRemovals = ConcurrentHashMap.newKeySet();
    private final Map<String, SensorView> pendingSensorChanges = new ConcurrentHashMap<>();

    private final SessionRegistry registry;

    public StreamHub(SessionRegistry registry) {
        this.registry = registry;
    }

    // ── Ingest (called by the Kafka consumers) ────────────────────────────────────────────────────

    public void onTrack(TrackView track) {
        tracks.put(track.trackId(), track);
        pendingTrackRemovals.remove(track.trackId());
        pendingTrackChanges.put(track.trackId(), track);
    }

    public void onTrackLost(String trackId) {
        tracks.remove(trackId);
        pendingTrackChanges.remove(trackId);
        pendingTrackRemovals.add(trackId);
    }

    public void onSensor(SensorView sensor) {
        sensors.put(sensor.sensorId(), sensor);
        pendingSensorChanges.put(sensor.sensorId(), sensor);
    }

    /** An alert is never coalesced — pushed immediately to every session watching the alerts channel. */
    public void onAlert(AlertView alert) {
        ServerMessages.Alert message = ServerMessages.Alert.of(alert);
        registry.subscribersOf(Channel.ALERTS).forEach(session -> session.send(message));
    }

    // ── Snapshots (called by the WebSocket handler on subscribe / resnapshot) ──────────────────────

    /** The current full payload for a channel, or empty for a channel that carries no snapshot. */
    public Object snapshotPayload(Channel channel) {
        return switch (channel) {
            case TRACKS -> new TracksSnapshot(new ArrayList<>(tracks.values()));
            case SENSORS -> new SensorsSnapshot(new ArrayList<>(sensors.values()));
            case ALERTS, SYSTEM -> null;   // alerts stream live; system carries no state
        };
    }

    // ── Fan-out (called by the flush scheduler) ───────────────────────────────────────────────────

    /** Drains the coalesced track and sensor changes and sends a DELTA to each subscriber. */
    public void flush() {
        flushTracks();
        flushSensors();
    }

    private void flushTracks() {
        if (pendingTrackChanges.isEmpty() && pendingTrackRemovals.isEmpty()) {
            return;
        }
        Map<String, TrackView> changed = drain(pendingTrackChanges);
        List<String> removed = drainRemovals();
        if (changed.isEmpty() && removed.isEmpty()) {
            return;
        }
        TracksDelta payload = new TracksDelta(new ArrayList<>(changed.values()), removed);
        registry.subscribersOf(Channel.TRACKS).forEach(session ->
                session.send(ServerMessages.Delta.of(Channel.TRACKS, session.nextSeq(Channel.TRACKS),
                        payload)));
    }

    private void flushSensors() {
        if (pendingSensorChanges.isEmpty()) {
            return;
        }
        Map<String, SensorView> changed = drain(pendingSensorChanges);
        if (changed.isEmpty()) {
            return;
        }
        SensorsDelta payload = new SensorsDelta(new ArrayList<>(changed.values()));
        registry.subscribersOf(Channel.SENSORS).forEach(session ->
                session.send(ServerMessages.Delta.of(Channel.SENSORS, session.nextSeq(Channel.SENSORS),
                        payload)));
    }

    /** Copies the pending map and removes exactly the entries copied, so a concurrent newer value survives. */
    private static <T> Map<String, T> drain(Map<String, T> pending) {
        Map<String, T> copy = new HashMap<>(pending);
        copy.forEach(pending::remove);   // remove(key, value): a newer value put in between stays
        return copy;
    }

    private List<String> drainRemovals() {
        List<String> copy = new ArrayList<>(pendingTrackRemovals);
        pendingTrackRemovals.removeAll(copy);
        return copy;
    }

    // Visible for tests.
    int trackCount() {
        return tracks.size();
    }
}
