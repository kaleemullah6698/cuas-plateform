package pk.nust.cuas.gateway.realtime;

import java.util.Optional;

/**
 * A WebSocket subscription channel (ARCHITECTURE.md §2.5.2, §3.5). One multiplexed connection carries
 * several channels; a client subscribes by the wire names below.
 *
 * <p>{@code SYSTEM} carries connection-level notices; it is delivered to every session regardless of
 * an explicit subscription, so it is not something a client subscribes to here.
 */
public enum Channel {
    TRACKS("tracks"),
    ALERTS("alerts"),
    SENSORS("sensors"),
    SYSTEM("system");

    private final String wireName;

    Channel(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }

    /** @return the channel for a wire name, or empty if it is not a subscribable channel name */
    public static Optional<Channel> fromWire(String wireName) {
        for (Channel channel : values()) {
            if (channel.wireName.equals(wireName)) {
                return Optional.of(channel);
            }
        }
        return Optional.empty();
    }
}
