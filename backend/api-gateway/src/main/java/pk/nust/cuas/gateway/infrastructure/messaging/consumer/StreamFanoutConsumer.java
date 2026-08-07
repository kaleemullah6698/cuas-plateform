package pk.nust.cuas.gateway.infrastructure.messaging.consumer;

import com.google.protobuf.InvalidProtocolBufferException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import pk.nust.cuas.alert.v1.Alert;
import pk.nust.cuas.gateway.realtime.StreamHub;
import pk.nust.cuas.gateway.realtime.StreamViewMapper;
import pk.nust.cuas.ingestion.v1.SensorHealth;
import pk.nust.cuas.track.v1.Track;

/**
 * Consumes the topics the gateway fans out (ARCHITECTURE.md §4.1.1): {@code track.created/updated/lost},
 * {@code alert.raised} and {@code sensor.health}. Each record updates the {@link StreamHub}'s live view;
 * the flush scheduler and the alert path then push to subscribers.
 *
 * <p>The consumer reads from {@code latest} (see application.yml): the gateway is a live edge, so a
 * restart resumes the current stream rather than replaying history. A malformed record is logged and
 * skipped — it will not parse on redelivery, and blocking the partition on it would stall the fan-out.
 */
@Component
public class StreamFanoutConsumer {

    private static final Logger log = LoggerFactory.getLogger(StreamFanoutConsumer.class);
    private static final String GROUP = "${spring.kafka.consumer.group-id:api-gateway-fanout}";

    private final StreamHub hub;
    private final StreamViewMapper mapper;

    public StreamFanoutConsumer(StreamHub hub, StreamViewMapper mapper) {
        this.hub = hub;
        this.mapper = mapper;
    }

    @KafkaListener(topics = {"track.created.v1", "track.updated.v1"}, groupId = GROUP)
    public void onTrack(ConsumerRecord<String, byte[]> record) {
        try {
            hub.onTrack(mapper.toTrackView(Track.parseFrom(record.value())));
        } catch (InvalidProtocolBufferException ex) {
            skip("track", record, ex);
        }
    }

    @KafkaListener(topics = "track.lost.v1", groupId = GROUP)
    public void onTrackLost(ConsumerRecord<String, byte[]> record) {
        try {
            hub.onTrackLost(Track.parseFrom(record.value()).getTrackId());
        } catch (InvalidProtocolBufferException ex) {
            skip("track.lost", record, ex);
        }
    }

    @KafkaListener(topics = "alert.raised.v1", groupId = GROUP)
    public void onAlertRaised(ConsumerRecord<String, byte[]> record) {
        try {
            hub.onAlert(mapper.toAlertView(Alert.parseFrom(record.value())));
        } catch (InvalidProtocolBufferException ex) {
            skip("alert.raised", record, ex);
        }
    }

    @KafkaListener(topics = "sensor.health.v1", groupId = GROUP)
    public void onSensorHealth(ConsumerRecord<String, byte[]> record) {
        try {
            hub.onSensor(mapper.toSensorView(SensorHealth.parseFrom(record.value())));
        } catch (InvalidProtocolBufferException ex) {
            skip("sensor.health", record, ex);
        }
    }

    private static void skip(String label, ConsumerRecord<String, byte[]> record, Exception ex) {
        log.warn("Skipping an unparseable {} record at offset {}", label, record.offset(), ex);
    }
}
