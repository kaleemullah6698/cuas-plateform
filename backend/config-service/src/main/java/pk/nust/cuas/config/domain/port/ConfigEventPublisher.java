package pk.nust.cuas.config.domain.port;

import pk.nust.cuas.config.domain.model.Configuration;

/**
 * Announces a configuration change on Kafka (ARCHITECTURE.md §4.1.12, §5.2).
 *
 * <p>An output port implemented by the Kafka adapter, which publishes to
 * {@code platform.config.updated.v1} always and to the kind-specific topic
 * ({@code platform.config.sensor/policy/ai.updated.v1}) when the namespace is one those consumers
 * watch. The topics are compact and keyed by namespace, so the latest value always survives
 * compaction for a late consumer.
 */
public interface ConfigEventPublisher {

    /**
     * Publishes the new current state of a namespace.
     *
     * @param configuration the namespace after the change
     */
    void publish(Configuration configuration);
}
