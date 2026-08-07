package pk.nust.cuas.config.infrastructure.messaging;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import pk.nust.cuas.config.domain.model.Configuration;
import pk.nust.cuas.config.domain.port.ConfigChangeNotifier;

/**
 * In-process fan-out of configuration changes to the open WatchConfig streams (ARCHITECTURE.md
 * §4.1.12).
 *
 * <p>The gRPC {@code WatchConfig} handler subscribes one listener per open stream; a committed change
 * is delivered to every subscriber synchronously. A single subscriber that throws (a broken stream)
 * is logged and skipped, never allowed to stop the fan-out to the others. The copy-on-write list makes
 * a subscribe or unsubscribe during a broadcast safe without locking the notify path.
 */
@Component
public class ConfigChangeBroadcaster implements ConfigChangeNotifier {

    private static final Logger log = LoggerFactory.getLogger(ConfigChangeBroadcaster.class);

    private final CopyOnWriteArrayList<Consumer<Configuration>> subscribers = new CopyOnWriteArrayList<>();

    /**
     * Registers a listener for future changes.
     *
     * @param listener the listener
     * @return a handle that removes it when run — call it when the stream closes
     */
    public Runnable subscribe(Consumer<Configuration> listener) {
        subscribers.add(listener);
        return () -> subscribers.remove(listener);
    }

    @Override
    public void notifyChanged(Configuration configuration) {
        for (Consumer<Configuration> subscriber : subscribers) {
            try {
                subscriber.accept(configuration);
            } catch (RuntimeException ex) {
                log.debug("A WatchConfig subscriber rejected an update; it will be dropped on close", ex);
            }
        }
    }

    int subscriberCount() {
        return subscribers.size();
    }
}
