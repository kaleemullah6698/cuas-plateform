package pk.nust.cuas.notification.domain.model;

/**
 * One channel a recipient has configured (section 4.1.9): its address, whether it is enabled, and its
 * preference order. Lower {@code priority} is preferred — the cross-channel choice picks the enabled
 * channel with the smallest priority, so one recipient is paged once per event, on their best channel.
 *
 * @param channel  the channel
 * @param address  the delivery address (email, phone, webhook URL; the user id for websocket)
 * @param enabled  whether the recipient wants this channel used
 * @param priority preference order, lower first
 */
public record ChannelPreference(Channel channel, String address, boolean enabled, int priority) {

    public ChannelPreference {
        if (channel == null) {
            throw new IllegalArgumentException("channel is required");
        }
        if (address == null || address.isBlank()) {
            throw new IllegalArgumentException("address is required");
        }
    }
}
