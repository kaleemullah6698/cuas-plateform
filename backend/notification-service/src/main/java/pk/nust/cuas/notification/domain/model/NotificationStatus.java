package pk.nust.cuas.notification.domain.model;

/**
 * The delivery status of a notification (ARCHITECTURE.md section 4.1.9).
 *
 * <p>{@code FAILED_RETRYABLE} is the only non-terminal failure — it carries a next-attempt time and
 * is swept back into delivery. {@code SUPPRESSED} means quiet hours or a preference stopped it before
 * any attempt; it is a deliberate non-delivery, not a failure.
 */
public enum NotificationStatus {
    PENDING,
    SENT,
    FAILED_RETRYABLE,
    FAILED,
    SUPPRESSED
}
