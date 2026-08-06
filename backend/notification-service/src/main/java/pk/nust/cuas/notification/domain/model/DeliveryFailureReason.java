package pk.nust.cuas.notification.domain.model;

/**
 * Why a delivery attempt failed (ARCHITECTURE.md section 4.1.9). Domain mirror of the contract enum.
 *
 * <p>The distinction that matters is {@link #isRetryable()}: a provider timeout is worth another
 * attempt, an unknown recipient never will be, and retrying a terminal failure would consume the
 * per-channel rate budget a genuinely retryable notification needs.
 */
public enum DeliveryFailureReason {
    PROVIDER_ERROR(true),
    PROVIDER_TIMEOUT(true),
    RATE_LIMITED(true),
    RECIPIENT_UNKNOWN(false),
    CHANNEL_DISABLED(false),
    REJECTED(false);

    private final boolean retryable;

    DeliveryFailureReason(boolean retryable) {
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
