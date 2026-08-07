package pk.nust.cuas.gateway.realtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.Timestamp;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import pk.nust.cuas.alert.v1.Alert;
import pk.nust.cuas.detection.v1.GeoPosition;
import pk.nust.cuas.gateway.realtime.view.AlertView;
import pk.nust.cuas.gateway.realtime.view.SensorView;
import pk.nust.cuas.gateway.realtime.view.TrackView;
import pk.nust.cuas.ingestion.v1.SensorHealth;
import pk.nust.cuas.threat.v1.ThreatLevel;
import pk.nust.cuas.track.v1.Track;
import pk.nust.cuas.track.v1.TrackClassification;
import pk.nust.cuas.track.v1.TrackStatus;

/** Mapping the Kafka protobuf events to the console view shapes. */
class StreamViewMapperTest {

    private final StreamViewMapper mapper = new StreamViewMapper();

    @Test
    void aTrackMapsItsPositionClassificationAndTiming() {
        String trackId = UUID.randomUUID().toString();
        Track track = Track.newBuilder()
                .setTrackId(trackId)
                .setSectorId("sector-NE")
                .setStatus(TrackStatus.TRACK_STATUS_CONFIRMED)
                .setPosition(GeoPosition.newBuilder().setLatitude(33.6).setLongitude(73.0)
                        .setAltitudeM(120.0))
                .setSpeedMs(14.0)
                .setHeadingDeg(270.0)
                .setClassification(TrackClassification.newBuilder().setLabel("DJI_Mavic_3")
                        .setConfidence(0.87f))
                .setLastSeen(Timestamp.newBuilder().setSeconds(1_800_000_000L))
                .setDetectionCount(5)
                .build();

        TrackView view = mapper.toTrackView(track);

        assertThat(view.trackId()).isEqualTo(trackId);
        assertThat(view.latitude()).isEqualTo(33.6);
        assertThat(view.altitudeM()).isEqualTo(120.0);
        assertThat(view.classLabel()).isEqualTo("DJI_Mavic_3");
        assertThat(view.confidence()).isEqualTo(0.87, org.assertj.core.data.Offset.offset(1e-6));
        assertThat(view.lastSeen()).isEqualTo("2027-01-15T08:00:00Z");
        assertThat(view.detectionCount()).isEqualTo(5);
    }

    @Test
    void anUnclassifiedTrackHasNoLabelOrConfidence() {
        TrackView view = mapper.toTrackView(Track.newBuilder()
                .setTrackId(UUID.randomUUID().toString())
                .setStatus(TrackStatus.TRACK_STATUS_TENTATIVE)
                .build());

        assertThat(view.classLabel()).isNull();
        assertThat(view.confidence()).isNull();
        assertThat(view.latitude()).isNull();   // no position set
    }

    @Test
    void anAlertMapsItsSeverityAndReferences() {
        Alert alert = Alert.newBuilder()
                .setAlertId("a1").setReference("ALT-9912")
                .setSeverity(ThreatLevel.THREAT_LEVEL_CRITICAL)
                .setTrackId("t1").setTitle("CRITICAL — TRK closing").setSummary("closing on SITE-1")
                .build();

        AlertView view = mapper.toAlertView(alert);

        assertThat(view.severity()).isEqualTo("THREAT_LEVEL_CRITICAL");
        assertThat(view.reference()).isEqualTo("ALT-9912");
        assertThat(view.title()).isEqualTo("CRITICAL — TRK closing");
    }

    @Test
    void aSensorMapsItsStatus() {
        SensorView view = mapper.toSensorView(SensorHealth.newBuilder()
                .setSensorId("RF-NE-02").setStatus(SensorHealth.Status.OFFLINE).build());

        assertThat(view.sensorId()).isEqualTo("RF-NE-02");
        assertThat(view.status()).isEqualTo("OFFLINE");
        assertThat(view.lastSeen()).isNull();
    }
}
