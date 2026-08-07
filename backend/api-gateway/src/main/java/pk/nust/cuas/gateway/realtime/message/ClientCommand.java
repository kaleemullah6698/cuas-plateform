package pk.nust.cuas.gateway.realtime.message;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * A client → server WebSocket command (ARCHITECTURE.md §2.5.2, §3.5). The client subscribes to
 * channels, and requests a fresh snapshot when it detects a {@code seq} gap.
 *
 * <p>Lenient on unknown fields: the {@code filter} object §3.5 allows (e.g. {@code {sector:NE}}) is
 * accepted on the wire but not yet applied — sector filtering is a documented follow-on, so an old or
 * new client is never rejected for sending it.
 *
 * @param type     "SUBSCRIBE", "RESNAPSHOT" or "UNSUBSCRIBE"
 * @param channels the channels to subscribe to (SUBSCRIBE)
 * @param channel  the single channel to resnapshot or unsubscribe
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ClientCommand(String type, List<String> channels, String channel) {

    public static final String SUBSCRIBE = "SUBSCRIBE";
    public static final String RESNAPSHOT = "RESNAPSHOT";
    public static final String UNSUBSCRIBE = "UNSUBSCRIBE";
}
