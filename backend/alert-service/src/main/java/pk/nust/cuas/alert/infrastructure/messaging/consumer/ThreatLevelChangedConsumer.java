package pk.nust.cuas.alert.infrastructure.messaging.consumer;

import com.google.protobuf.InvalidProtocolBufferException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import pk.nust.cuas.alert.application.AlertLifecycleService;
import pk.nust.cuas.alert.common.AlertProtoMapper;
import pk.nust.cuas.alert.infrastructure.messaging.AlertTopics;
import pk.nust.cuas.threat.v1.ThreatLevelChanged;

/**
 * Consumes {@code threat.level.changed.v1} and raises alerts (ARCHITECTURE.md sections 4.1.7, 4.1.8).
 *
 * <p>This is the service's primary input. threat-assessment publishes here <em>only on a
 * transition</em>, which is what keeps one alert from being raised per track update; the raising
 * policy and the suppression window then decide which transitions are worth interrupting somebody
 * for.
 *
 * <p><b>A malformed message is acknowledged, not retried.</b> A record that cannot be parsed will
 * not parse next time either, and blocking the partition on it would stop every later alert behind
 * one poison record — the failure section 5.6 exists to prevent, and the one that would leave an
 * operator with a silent board. The topic's DLQ is provisioned; routing to it is the M8 hardening
 * item already recorded for the pipeline's other consumers.
 */
@Component
public class ThreatLevelChangedConsumer {

    private static final Logger log = LoggerFactory.getLogger(ThreatLevelChangedConsumer.class);

    private final AlertLifecycleService alerts;
    private final AlertProtoMapper mapper;

    public ThreatLevelChangedConsumer(AlertLifecycleService alerts, AlertProtoMapper mapper) {
        this.alerts = alerts;
        this.mapper = mapper;
    }

    @KafkaListener(topics = AlertTopics.THREAT_LEVEL_CHANGED,
            groupId = "${spring.kafka.consumer.group-id:alert-service}")
    public void onThreatLevelChanged(ConsumerRecord<String, byte[]> record) {
        try {
            ThreatLevelChanged message = ThreatLevelChanged.parseFrom(record.value());
            mapper.toTransition(message).ifPresentOrElse(
                    alerts::onThreatTransition,
                    () -> log.warn("Skipping a threat transition with no usable track or level at"
                            + " offset {}", record.offset()));
        } catch (InvalidProtocolBufferException ex) {
            log.warn("Skipping an unparseable threat transition at offset {}", record.offset(), ex);
        }
    }
}
