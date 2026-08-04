package pk.nust.cuas.ingestion.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pk.nust.cuas.ingestion.application.port.SensorHealthPublisher;
import pk.nust.cuas.ingestion.domain.model.HealthStatus;
import pk.nust.cuas.ingestion.domain.model.SensorHealthSnapshot;
import pk.nust.cuas.ingestion.domain.policy.HealthStatusPolicy;
import pk.nust.cuas.ingestion.domain.port.SensorStateStore;

@ExtendWith(MockitoExtension.class)
class HealthEmissionServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-04T12:00:00Z");

    @Mock
    private SensorStateStore stateStore;
    @Mock
    private SensorHealthPublisher publisher;

    private HealthEmissionService service() {
        return new HealthEmissionService(
                stateStore,
                new HealthStatusPolicy(Duration.ofSeconds(30), Duration.ofMinutes(2)),
                publisher,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void recomputesStatusFromLastSeenAndPublishesEachSensor() {
        SensorHealthSnapshot fresh = new SensorHealthSnapshot(
                "rf-01", HealthStatus.OFFLINE, Optional.of(NOW.minusSeconds(5)), 12L); // stale stored status
        SensorHealthSnapshot silent = new SensorHealthSnapshot(
                "radar-1", HealthStatus.ONLINE, Optional.of(NOW.minusSeconds(600)), 0L);
        when(stateStore.allHealth()).thenReturn(List.of(fresh, silent));

        service().emitAll();

        // The freshly-seen sensor is recomputed ONLINE despite its stored OFFLINE; the silent one OFFLINE.
        verify(stateStore).updateStatus("rf-01", HealthStatus.ONLINE);
        verify(stateStore).updateStatus("radar-1", HealthStatus.OFFLINE);

        ArgumentCaptor<SensorHealthSnapshot> published = ArgumentCaptor.forClass(SensorHealthSnapshot.class);
        verify(publisher, org.mockito.Mockito.times(2)).publish(published.capture());
        assertThat(published.getAllValues())
                .anySatisfy(s -> {
                    assertThat(s.sensorId()).isEqualTo("rf-01");
                    assertThat(s.status()).isEqualTo(HealthStatus.ONLINE);
                })
                .anySatisfy(s -> {
                    assertThat(s.sensorId()).isEqualTo("radar-1");
                    assertThat(s.status()).isEqualTo(HealthStatus.OFFLINE);
                });
    }

    @Test
    void doesNothingWhenNoSensorsAreKnown() {
        when(stateStore.allHealth()).thenReturn(List.of());

        service().emitAll();

        verify(publisher, org.mockito.Mockito.never()).publish(org.mockito.ArgumentMatchers.any());
    }
}
