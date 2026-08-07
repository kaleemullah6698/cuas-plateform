package pk.nust.cuas.config.domain.port;

import pk.nust.cuas.config.domain.model.Configuration;

/**
 * Pushes a configuration change to the in-process WatchConfig subscribers (ARCHITECTURE.md §4.1.12).
 *
 * <p>Separate from {@link ConfigEventPublisher}: the Kafka event is the durable mirror, while this is
 * the live server-stream that §4.1.12 makes the authoritative refresh mechanism — a watching service
 * receives the new value on its open stream the moment the change commits, with no poll. The adapter
 * is an in-memory broadcaster the gRPC {@code WatchConfig} handler subscribes to.
 */
public interface ConfigChangeNotifier {

    /**
     * Notifies every subscriber of a change.
     *
     * @param configuration the namespace after the change
     */
    void notifyChanged(Configuration configuration);
}
