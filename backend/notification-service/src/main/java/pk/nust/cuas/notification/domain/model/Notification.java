package pk.nust.cuas.notification.domain.model;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * A notification: one recipient notified about one thing, on one channel (ARCHITECTURE.md section
 * 4.1.9). The aggregate root.
 *
 * <p>Immutable (CLAUDE.md section 3): each delivery outcome returns a new {@code Notification}, so the
 * before and after are both available to the caller that persists and publishes them. A notification
 * is opened {@code PENDING}, then delivered — {@code sent}, {@code failedRetryable} (with a next
 * attempt), or {@code failedTerminal} — or {@code suppressed} before any attempt by quiet hours.
 */
public final class Notification {

    private final UUID notificationId;
    private final UUID recipientUserId;
    private final Channel channel;
    private final NotificationTrigger trigger;
    private final String dedupKey;
    private final Urgency urgency;
    private final SubjectRef refs;
    private final String subject;
    private final String correlationId;
    private final NotificationStatus status;
    private final int attempt;
    private final Instant createdAt;
    private final Optional<Instant> sentAt;
    private final Optional<Instant> failedAt;
    private final Optional<Instant> nextAttemptAt;
    private final Optional<DeliveryFailureReason> reason;
    private final Optional<String> providerMessageId;

    private Notification(UUID notificationId, UUID recipientUserId, Channel channel,
                         NotificationTrigger trigger, String dedupKey, Urgency urgency, SubjectRef refs,
                         String subject, String correlationId, NotificationStatus status, int attempt,
                         Instant createdAt, Optional<Instant> sentAt, Optional<Instant> failedAt,
                         Optional<Instant> nextAttemptAt, Optional<DeliveryFailureReason> reason,
                         Optional<String> providerMessageId) {
        this.notificationId = notificationId;
        this.recipientUserId = recipientUserId;
        this.channel = channel;
        this.trigger = trigger;
        this.dedupKey = dedupKey;
        this.urgency = urgency;
        this.refs = refs;
        this.subject = subject;
        this.correlationId = correlationId;
        this.status = status;
        this.attempt = attempt;
        this.createdAt = createdAt;
        this.sentAt = sentAt;
        this.failedAt = failedAt;
        this.nextAttemptAt = nextAttemptAt;
        this.reason = reason;
        this.providerMessageId = providerMessageId;
    }

    /** Opens a new pending notification. */
    public static Notification open(UUID id, UUID recipientUserId, Channel channel,
                                    NotificationTrigger trigger, String dedupKey, Urgency urgency,
                                    SubjectRef refs, String subject, String correlationId,
                                    Instant createdAt) {
        return new Notification(id, recipientUserId, channel, trigger, dedupKey, urgency, refs, subject,
                correlationId, NotificationStatus.PENDING, 0, createdAt,
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    /** Reconstitutes from storage. */
    public static Notification reconstitute(UUID id, UUID recipientUserId, Channel channel,
            NotificationTrigger trigger, String dedupKey, Urgency urgency, SubjectRef refs,
            String subject, String correlationId, NotificationStatus status, int attempt,
            Instant createdAt, Instant sentAt, Instant failedAt, Instant nextAttemptAt,
            DeliveryFailureReason reason, String providerMessageId) {
        return new Notification(id, recipientUserId, channel, trigger, dedupKey, urgency, refs, subject,
                correlationId, status, attempt, createdAt, Optional.ofNullable(sentAt),
                Optional.ofNullable(failedAt), Optional.ofNullable(nextAttemptAt),
                Optional.ofNullable(reason), Optional.ofNullable(providerMessageId));
    }

    /** The notification was delivered. */
    public Notification sent(Optional<String> providerMessageId, Instant at) {
        return copy(NotificationStatus.SENT, attempt + 1, Optional.of(at), Optional.empty(),
                Optional.empty(), Optional.empty(), providerMessageId);
    }

    /** The attempt failed but is worth retrying at {@code nextAttemptAt}. */
    public Notification failedRetryable(DeliveryFailureReason reason, Instant at, Instant nextAttemptAt) {
        return copy(NotificationStatus.FAILED_RETRYABLE, attempt + 1, sentAt, Optional.of(at),
                Optional.of(nextAttemptAt), Optional.of(reason), providerMessageId);
    }

    /** The attempt failed terminally — no further retry. */
    public Notification failedTerminal(DeliveryFailureReason reason, Instant at) {
        return copy(NotificationStatus.FAILED, attempt + 1, sentAt, Optional.of(at),
                Optional.empty(), Optional.of(reason), providerMessageId);
    }

    /** Quiet hours or a preference stopped it before any attempt. */
    public Notification suppressed() {
        return copy(NotificationStatus.SUPPRESSED, attempt, sentAt, failedAt, Optional.empty(),
                reason, providerMessageId);
    }

    private Notification copy(NotificationStatus status, int attempt, Optional<Instant> sentAt,
                              Optional<Instant> failedAt, Optional<Instant> nextAttemptAt,
                              Optional<DeliveryFailureReason> reason, Optional<String> providerMessageId) {
        return new Notification(notificationId, recipientUserId, channel, trigger, dedupKey, urgency,
                refs, subject, correlationId, status, attempt, createdAt, sentAt, failedAt, nextAttemptAt,
                reason, providerMessageId);
    }

    public UUID notificationId() {
        return notificationId;
    }

    public UUID recipientUserId() {
        return recipientUserId;
    }

    public Channel channel() {
        return channel;
    }

    public NotificationTrigger trigger() {
        return trigger;
    }

    public String dedupKey() {
        return dedupKey;
    }

    public Urgency urgency() {
        return urgency;
    }

    public SubjectRef refs() {
        return refs;
    }

    public String subject() {
        return subject;
    }

    public String correlationId() {
        return correlationId;
    }

    public NotificationStatus status() {
        return status;
    }

    public int attempt() {
        return attempt;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Optional<Instant> sentAt() {
        return sentAt;
    }

    public Optional<Instant> failedAt() {
        return failedAt;
    }

    public Optional<Instant> nextAttemptAt() {
        return nextAttemptAt;
    }

    public Optional<DeliveryFailureReason> reason() {
        return reason;
    }

    public Optional<String> providerMessageId() {
        return providerMessageId;
    }
}
