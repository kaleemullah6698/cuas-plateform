package pk.nust.cuas.gateway.realtime.message;

import pk.nust.cuas.gateway.realtime.Channel;
import pk.nust.cuas.gateway.realtime.view.AlertView;

/**
 * The server → client WebSocket messages (ARCHITECTURE.md §3.5). Each is a JSON object with a {@code
 * type} discriminator; a factory sets it so the wire shape matches §3.5 exactly. The frontend's
 * {@code ServerMessage} union mirrors these.
 */
public final class ServerMessages {

    private ServerMessages() {
    }

    /** Sent once, immediately after the handshake. */
    public record Connected(String type, String sessionId, String serverTime) {
        public static Connected of(String sessionId, String serverTime) {
            return new Connected("CONNECTED", sessionId, serverTime);
        }
    }

    /** The full state of a channel on subscribe or resnapshot. */
    public record Snapshot(String type, String channel, long seq, Object payload) {
        public static Snapshot of(Channel channel, long seq, Object payload) {
            return new Snapshot("SNAPSHOT", channel.wireName(), seq, payload);
        }
    }

    /** An incremental change on a channel, carrying the next monotonic seq. */
    public record Delta(String type, String channel, long seq, Object payload) {
        public static Delta of(Channel channel, long seq, Object payload) {
            return new Delta("DELTA", channel.wireName(), seq, payload);
        }
    }

    /** An alert — never coalesced. */
    public record Alert(String type, AlertView alert) {
        public static Alert of(AlertView alert) {
            return new Alert("ALERT", alert);
        }
    }

    /** The 5-second liveness beat (§2.5.2). */
    public record Heartbeat(String type, String serverTime) {
        public static Heartbeat of(String serverTime) {
            return new Heartbeat("HEARTBEAT", serverTime);
        }
    }

    /** A protocol or authorisation error; {@code retryable} tells the client whether to try again. */
    public record Error(String type, String code, String message, boolean retryable) {
        public static Error of(String code, String message, boolean retryable) {
            return new Error("ERROR", code, message, retryable);
        }
    }
}
