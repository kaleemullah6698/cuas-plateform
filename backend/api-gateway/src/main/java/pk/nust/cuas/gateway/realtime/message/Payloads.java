package pk.nust.cuas.gateway.realtime.message;

import java.util.List;
import pk.nust.cuas.gateway.realtime.view.SensorView;
import pk.nust.cuas.gateway.realtime.view.TrackView;

/** The {@code payload} shapes carried inside SNAPSHOT and DELTA messages (ARCHITECTURE.md §3.5). */
public final class Payloads {

    private Payloads() {
    }

    /** A full tracks snapshot. */
    public record TracksSnapshot(List<TrackView> tracks) {
    }

    /**
     * A tracks delta: the tracks that changed since the last flush, and the ids of tracks removed
     * (lost). A client applies {@code changed} by id and drops {@code removed}.
     */
    public record TracksDelta(List<TrackView> changed, List<String> removed) {
    }

    /** A full sensors snapshot. */
    public record SensorsSnapshot(List<SensorView> sensors) {
    }

    /** A sensors delta: the sensors whose health changed since the last flush. */
    public record SensorsDelta(List<SensorView> changed) {
    }
}
