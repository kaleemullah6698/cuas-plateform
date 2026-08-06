package pk.nust.cuas.notification.infrastructure.messaging;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import pk.nust.cuas.alert.v1.Alert;
import pk.nust.cuas.ingestion.v1.SensorHealth;
import pk.nust.cuas.notification.application.NotificationDispatchService;
import pk.nust.cuas.notification.domain.model.NotificationTrigger;
import pk.nust.cuas.notification.domain.model.TriggerEvent;
import pk.nust.cuas.threat.v1.ThreatLevel;

/**
 * The consumer's parse-map-dispatch behaviour, exercised by handing the listener a record directly —
 * no broker, because what is under test is the decoding and filtering, not Kafka delivery.
 */
class SourceEventConsumerTest {

    private final NotificationDispatchService dispatch = mock(NotificationDispatchService.class);
    private final SourceEventConsumer consumer = new SourceEventConsumer(dispatch, new SourceEventMapper());

    @Test
    void aParsedAlertIsMappedAndDispatched() {
        Alert alert = Alert.newBuilder()
                .setAlertId(UUID.randomUUID().toString())
                .setDedupKey("dk-1")
                .setSeverity(ThreatLevel.THREAT_LEVEL_HIGH)
                .setTitle("t")
                .setCorrelationId("corr-1")
                .build();

        consumer.onAlertRaised(record("alert.raised.v1", alert.toByteArray()));

        ArgumentCaptor<TriggerEvent> captor = ArgumentCaptor.forClass(TriggerEvent.class);
        verify(dispatch).dispatch(captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().trigger())
                .isEqualTo(NotificationTrigger.ALERT_RAISED);
    }

    @Test
    void anUnparseableRecordIsAcknowledgedNotDispatched() {
        consumer.onAlertRaised(record("alert.raised.v1", new byte[]{0x42, 0x13, 0x37}));

        verify(dispatch, never()).dispatch(any());
    }

    @Test
    void aSensorThatIsNotOfflineIsIgnored() {
        SensorHealth online = SensorHealth.newBuilder()
                .setSensorId("RF-NE-02")
                .setStatus(SensorHealth.Status.ONLINE)
                .build();

        consumer.onSensorHealth(record("sensor.health.v1", online.toByteArray()));

        verify(dispatch, never()).dispatch(any());
    }

    @Test
    void anOfflineSensorIsDispatched() {
        SensorHealth offline = SensorHealth.newBuilder()
                .setSensorId("RF-NE-02")
                .setStatus(SensorHealth.Status.OFFLINE)
                .build();

        consumer.onSensorHealth(record("sensor.health.v1", offline.toByteArray()));

        verify(dispatch).dispatch(any());
    }

    private static ConsumerRecord<String, byte[]> record(String topic, byte[] value) {
        return new ConsumerRecord<>(topic, 0, 0L, "key", value);
    }
}
