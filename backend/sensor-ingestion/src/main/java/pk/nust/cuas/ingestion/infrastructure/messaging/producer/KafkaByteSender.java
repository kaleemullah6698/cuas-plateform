package pk.nust.cuas.ingestion.infrastructure.messaging.producer;

import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import pk.nust.cuas.ingestion.domain.exception.IngestionUnavailableException;

/**
 * The one place a serialized protobuf message is handed to Kafka, keyed for partitioning.
 *
 * <p>Two send modes for two kinds of message. A detection is sent synchronously: the caller waits
 * for the broker acknowledgement so an intake ack or a consumer offset only advances behind a
 * durable write, and a failure becomes a {@link IngestionUnavailableException} the caller can act
 * on. Health telemetry is sent best-effort: a lost periodic tick is not worth blocking the emitter
 * for, so a failure is logged and swallowed.
 */
@Component
public class KafkaByteSender {

    private static final Logger log = LoggerFactory.getLogger(KafkaByteSender.class);
    private static final long SEND_TIMEOUT_SECONDS = 5;

    private final KafkaTemplate<String, byte[]> kafkaTemplate;

    public KafkaByteSender(KafkaTemplate<String, byte[]> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Sends and blocks until the broker acknowledges, translating any failure into a 503-mapped
     * domain exception.
     */
    void sendSync(String topic, String key, byte[] payload) {
        try {
            kafkaTemplate.send(topic, key, payload).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IngestionUnavailableException("Interrupted publishing to " + topic, ex);
        } catch (Exception ex) {
            throw new IngestionUnavailableException("Could not publish to " + topic, ex);
        }
    }

    /** Sends without waiting; logs a failure rather than propagating it. */
    void sendBestEffort(String topic, String key, byte[] payload) {
        kafkaTemplate.send(topic, key, payload).whenComplete((result, ex) -> {
            if (ex != null) {
                log.warn("Best-effort publish to {} for key '{}' failed", topic, key, ex);
            }
        });
    }
}
