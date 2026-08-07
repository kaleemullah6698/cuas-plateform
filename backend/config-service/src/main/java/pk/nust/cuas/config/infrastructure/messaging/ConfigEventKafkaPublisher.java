package pk.nust.cuas.config.infrastructure.messaging;

import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import pk.nust.cuas.config.domain.model.ConfigNamespaceKind;
import pk.nust.cuas.config.domain.model.Configuration;
import pk.nust.cuas.config.domain.port.ConfigEventPublisher;

/**
 * Kafka adapter publishing configuration changes (ARCHITECTURE.md §4.1.12, §5.2).
 *
 * <p>Every change goes to {@code platform.config.updated.v1}; sensor, policy and AI changes also go
 * to their kind-specific topic, so a consumer that only cares about policy subscribes to one topic
 * rather than filtering the firehose. All are compact and keyed by namespace.
 *
 * <p><b>Publication is best-effort.</b> The authoritative refresh mechanism §4.1.12 names is the
 * WatchConfig stream, which is DB-backed and re-reads current state on reconnect; the Kafka topics are
 * a durable mirror for Kafka-native consumers. The change is already committed before this runs, so a
 * send failure is logged and dropped rather than allowed to fail a change that durably took.
 */
@Component
public class ConfigEventKafkaPublisher implements ConfigEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(ConfigEventKafkaPublisher.class);

    private final KafkaTemplate<String, byte[]> kafka;
    private final ConfigProtoMapper mapper;

    public ConfigEventKafkaPublisher(KafkaTemplate<String, byte[]> kafka, ConfigProtoMapper mapper) {
        this.kafka = kafka;
        this.mapper = mapper;
    }

    @Override
    public void publish(Configuration configuration) {
        byte[] payload = mapper.toProto(configuration).toByteArray();
        String namespace = configuration.namespace();
        send(ConfigTopics.CONFIG_UPDATED, namespace, payload);
        kindTopic(configuration.kind()).ifPresent(topic -> send(topic, namespace, payload));
    }

    private static Optional<String> kindTopic(ConfigNamespaceKind kind) {
        return switch (kind) {
            case SENSOR -> Optional.of(ConfigTopics.CONFIG_SENSOR);
            case POLICY -> Optional.of(ConfigTopics.CONFIG_POLICY);
            case AI -> Optional.of(ConfigTopics.CONFIG_AI);
            default -> Optional.empty();
        };
    }

    private void send(String topic, String key, byte[] payload) {
        kafka.send(topic, key, payload).whenComplete((result, ex) -> {
            if (ex != null) {
                log.warn("Could not publish configuration change of '{}' to {}", key, topic, ex);
            }
        });
    }
}
