package pk.nust.cuas.notification.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** JPA mapping for {@code notification_log} — one notification and its current delivery status. */
@Entity
@Table(name = "notification_log")
public class NotificationLogEntity {

    @Id
    @Column(name = "notification_id")
    private UUID notificationId;

    @Column(name = "recipient_user_id", nullable = false)
    private UUID recipientUserId;

    @Column(name = "channel", nullable = false)
    private String channel;

    @Column(name = "trigger", nullable = false)
    private String trigger;

    @Column(name = "dedup_key", nullable = false)
    private String dedupKey;

    @Column(name = "urgency", nullable = false)
    private String urgency;

    @Column(name = "subject", nullable = false)
    private String subject;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "attempt", nullable = false)
    private int attempt;

    @Column(name = "alert_id")
    private UUID alertId;

    @Column(name = "incident_id")
    private UUID incidentId;

    @Column(name = "track_id")
    private UUID trackId;

    @Column(name = "reason")
    private String reason;

    @Column(name = "provider_message_id")
    private String providerMessageId;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "correlation_id")
    private String correlationId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "failed_at")
    private Instant failedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected NotificationLogEntity() {
    }

    public NotificationLogEntity(UUID notificationId, UUID recipientUserId, String channel, String trigger,
                                 String dedupKey, String urgency, String subject, String status, int attempt,
                                 UUID alertId, UUID incidentId, UUID trackId, String correlationId,
                                 Instant createdAt) {
        this.notificationId = notificationId;
        this.recipientUserId = recipientUserId;
        this.channel = channel;
        this.trigger = trigger;
        this.dedupKey = dedupKey;
        this.urgency = urgency;
        this.subject = subject;
        this.status = status;
        this.attempt = attempt;
        this.alertId = alertId;
        this.incidentId = incidentId;
        this.trackId = trackId;
        this.correlationId = correlationId;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    /** Applies a transition: status, attempt, timing, and the failure/provider fields. */
    public void apply(String status, int attempt, String reason, String providerMessageId,
                      Instant nextAttemptAt, Instant sentAt, Instant failedAt, Instant updatedAt) {
        this.status = status;
        this.attempt = attempt;
        this.reason = reason;
        this.providerMessageId = providerMessageId;
        this.nextAttemptAt = nextAttemptAt;
        this.sentAt = sentAt;
        this.failedAt = failedAt;
        this.updatedAt = updatedAt;
    }

    /** Claims a retry by pushing the next-attempt time out by the lease, so a peer sweep skips it. */
    public void leaseRetry(Instant leaseUntil) {
        this.nextAttemptAt = leaseUntil;
        this.updatedAt = leaseUntil;
    }

    public UUID notificationId() {
        return notificationId;
    }

    public UUID recipientUserId() {
        return recipientUserId;
    }

    public String channel() {
        return channel;
    }

    public String trigger() {
        return trigger;
    }

    public String dedupKey() {
        return dedupKey;
    }

    public String urgency() {
        return urgency;
    }

    public String subject() {
        return subject;
    }

    public String status() {
        return status;
    }

    public int attempt() {
        return attempt;
    }

    public UUID alertId() {
        return alertId;
    }

    public UUID incidentId() {
        return incidentId;
    }

    public UUID trackId() {
        return trackId;
    }

    public String reason() {
        return reason;
    }

    public String providerMessageId() {
        return providerMessageId;
    }

    public Instant nextAttemptAt() {
        return nextAttemptAt;
    }

    public String correlationId() {
        return correlationId;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant sentAt() {
        return sentAt;
    }

    public Instant failedAt() {
        return failedAt;
    }
}
