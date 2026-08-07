package pk.nust.cuas.config.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import pk.nust.cuas.config.domain.model.ConfigNamespaceKind;
import pk.nust.cuas.config.domain.model.Configuration;

/** The in-process fan-out to WatchConfig subscribers. */
class ConfigChangeBroadcasterTest {

    private final ConfigChangeBroadcaster broadcaster = new ConfigChangeBroadcaster();

    @Test
    void aSubscriberReceivesChangesUntilItUnsubscribes() {
        List<String> received = new ArrayList<>();
        Runnable unsubscribe = broadcaster.subscribe(c -> received.add(c.namespace()));

        broadcaster.notifyChanged(config("policy"));
        assertThat(received).containsExactly("policy");

        unsubscribe.run();
        broadcaster.notifyChanged(config("sensor"));
        assertThat(received).containsExactly("policy");
        assertThat(broadcaster.subscriberCount()).isZero();
    }

    @Test
    void aThrowingSubscriberDoesNotStopTheFanOutToOthers() {
        List<String> received = new ArrayList<>();
        broadcaster.subscribe(c -> {
            throw new IllegalStateException("broken stream");
        });
        broadcaster.subscribe(c -> received.add(c.namespace()));

        broadcaster.notifyChanged(config("policy"));

        assertThat(received).containsExactly("policy");
    }

    private static Configuration config(String namespace) {
        return Configuration.initial(namespace, ConfigNamespaceKind.GENERAL, "{}", "u", "j", Instant.now());
    }
}
