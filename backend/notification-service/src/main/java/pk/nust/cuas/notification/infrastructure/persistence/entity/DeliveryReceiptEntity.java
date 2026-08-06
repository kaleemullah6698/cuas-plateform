package pk.nust.cuas.notification.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** JPA mapping for {@code delivery_receipts} — one row per delivery attempt on a notification. */
@Entity
@Table(name = "delivery_receipts")
public class DeliveryReceiptEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "notification_id", nullable = false)
    private UUID notificationId;

    @Column(name = "attempt", nullable = false)
    private int attempt;

    @Column(name = "outcome", nullable = false)
    private String outcome;

    @Column(name = "channel", nullable = false)
    private String channel;

    @Column(name = "provider_message_id")
    private String providerMessageId;

    @Column(name = "reason")
    private String reason;

    @Column(name = "detail")
    private String detail;

    @Column(name = "latency_ms")
    private Integer latencyMs;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected DeliveryReceiptEntity() {
    }

    public DeliveryReceiptEntity(UUID notificationId, int attempt, String outcome, String channel,
                                 String providerMessageId, String reason, String detail, Integer latencyMs,
                                 Instant occurredAt) {
        this.notificationId = notificationId;
        this.attempt = attempt;
        this.outcome = outcome;
        this.channel = channel;
        this.providerMessageId = providerMessageId;
        this.reason = reason;
        this.detail = detail;
        this.latencyMs = latencyMs;
        this.occurredAt = occurredAt;
    }
}
