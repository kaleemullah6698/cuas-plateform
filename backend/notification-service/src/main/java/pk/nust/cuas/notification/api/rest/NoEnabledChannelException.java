package pk.nust.cuas.notification.api.rest;

/**
 * Raised when a test send is asked for but the caller has no enabled channel to deliver on. A 409:
 * the request was valid, but the account's current state cannot satisfy it until a channel is enabled.
 */
public class NoEnabledChannelException extends RuntimeException {

    public NoEnabledChannelException() {
        super("No enabled notification channel is configured.");
    }
}
