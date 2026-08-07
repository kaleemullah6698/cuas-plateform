package pk.nust.cuas.gateway.realtime;

import com.google.protobuf.Timestamp;
import java.time.Instant;
import org.springframework.stereotype.Component;
import pk.nust.cuas.alert.v1.Alert;
import pk.nust.cuas.gateway.realtime.view.AlertView;
import pk.nust.cuas.gateway.realtime.view.SensorView;
import pk.nust.cuas.gateway.realtime.view.TrackView;
import pk.nust.cuas.ingestion.v1.SensorHealth;
import pk.nust.cuas.track.v1.Track;

/**
 * Maps the Kafka protobuf events to the JSON view shapes the console consumes (ARCHITECTURE.md §3.5).
 * The generated contract types stay at this edge — the fan-out and the WebSocket layer work in views.
 */
@Component
public class StreamViewMapper {

    public TrackView toTrackView(Track track) {
        Double latitude = null;
        Double longitude = null;
        Double altitude = null;
        if (track.hasPosition()) {
            latitude = track.getPosition().getLatitude();
            longitude = track.getPosition().getLongitude();
            altitude = track.getPosition().getAltitudeM();
        }
        String classLabel = track.hasClassification() ? track.getClassification().getLabel() : null;
        Double confidence = track.hasClassification()
                ? (double) track.getClassification().getConfidence() : null;
        return new TrackView(track.getTrackId(), track.getSectorId(), track.getStatus().name(),
                latitude, longitude, altitude, track.getSpeedMs(), track.getHeadingDeg(),
                classLabel, confidence,
                iso(track.hasObservedAt() ? track.getObservedAt() : null),
                iso(track.hasLastSeen() ? track.getLastSeen() : null),
                track.getDetectionCount());
    }

    public AlertView toAlertView(Alert alert) {
        return new AlertView(alert.getAlertId(), alert.getReference(), alert.getSeverity().name(),
                alert.getTrackId(), alert.getTitle(), alert.getSummary(),
                iso(alert.hasRaisedAt() ? alert.getRaisedAt() : null));
    }

    public SensorView toSensorView(SensorHealth health) {
        return new SensorView(health.getSensorId(), health.getStatus().name(),
                iso(health.hasLastSeen() ? health.getLastSeen() : null));
    }

    private static String iso(Timestamp timestamp) {
        if (timestamp == null) {
            return null;
        }
        return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos()).toString();
    }
}
