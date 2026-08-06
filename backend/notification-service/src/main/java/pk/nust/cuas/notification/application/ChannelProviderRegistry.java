package pk.nust.cuas.notification.application;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import pk.nust.cuas.notification.application.port.ChannelProvider;
import pk.nust.cuas.notification.domain.model.Channel;

/**
 * Resolves the {@link ChannelProvider} for a channel (ARCHITECTURE.md section 4.1.9).
 *
 * <p>Built from every provider bean on the classpath, indexed by channel. Rejects a duplicate at
 * startup — two providers for one channel is a wiring mistake that must fail loudly, not silently
 * pick one. A channel with no provider is a configuration gap, so a lookup for it fails rather than
 * dropping the notification quietly.
 */
@Component
public class ChannelProviderRegistry {

    private final Map<Channel, ChannelProvider> providers = new EnumMap<>(Channel.class);

    public ChannelProviderRegistry(List<ChannelProvider> providers) {
        for (ChannelProvider provider : providers) {
            ChannelProvider existing = this.providers.putIfAbsent(provider.channel(), provider);
            if (existing != null) {
                throw new IllegalStateException("two providers for channel " + provider.channel());
            }
        }
    }

    /**
     * @param channel the channel to deliver on
     * @return the provider for it
     * @throws IllegalStateException if no provider is registered for the channel
     */
    public ChannelProvider forChannel(Channel channel) {
        ChannelProvider provider = providers.get(channel);
        if (provider == null) {
            throw new IllegalStateException("no provider registered for channel " + channel);
        }
        return provider;
    }
}
