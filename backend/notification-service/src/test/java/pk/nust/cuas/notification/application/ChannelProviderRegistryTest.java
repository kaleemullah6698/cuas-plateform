package pk.nust.cuas.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import pk.nust.cuas.notification.application.port.ChannelProvider;
import pk.nust.cuas.notification.application.port.DeliveryAttempt;
import pk.nust.cuas.notification.domain.model.Channel;
import pk.nust.cuas.notification.domain.model.DeliveryResult;

/** The channel-to-provider index built at startup by {@link ChannelProviderRegistry}. */
class ChannelProviderRegistryTest {

    @Test
    void itResolvesTheProviderForAChannel() {
        ChannelProvider email = stub(Channel.EMAIL);
        ChannelProviderRegistry registry = new ChannelProviderRegistry(List.of(email, stub(Channel.SMS)));

        assertThat(registry.forChannel(Channel.EMAIL)).isSameAs(email);
    }

    @Test
    void twoProvidersForOneChannelIsAWiringErrorThatFailsAtStartup() {
        assertThatThrownBy(() ->
                new ChannelProviderRegistry(List.of(stub(Channel.EMAIL), stub(Channel.EMAIL))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("EMAIL");
    }

    @Test
    void aChannelWithNoProviderFailsRatherThanDroppingTheNotification() {
        ChannelProviderRegistry registry = new ChannelProviderRegistry(List.of(stub(Channel.EMAIL)));

        assertThatThrownBy(() -> registry.forChannel(Channel.WEBHOOK))
                .isInstanceOf(IllegalStateException.class);
    }

    private static ChannelProvider stub(Channel channel) {
        return new ChannelProvider() {
            @Override
            public Channel channel() {
                return channel;
            }

            @Override
            public DeliveryResult deliver(DeliveryAttempt attempt) {
                return new DeliveryResult.Sent(Optional.empty(), 0L);
            }
        };
    }
}
