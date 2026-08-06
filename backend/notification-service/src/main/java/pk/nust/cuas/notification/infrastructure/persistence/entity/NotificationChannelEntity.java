package pk.nust.cuas.notification.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** JPA mapping for {@code notification_channels} — one delivery address per channel per user. */
@Entity
@Table(name = "notification_channels")
public class NotificationChannelEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "channel", nullable = false)
    private String channel;

    @Column(name = "address", nullable = false)
    private String address;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "priority", nullable = false)
    private int priority;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected NotificationChannelEntity() {
    }

    public NotificationChannelEntity(UUID userId, String channel, String address, boolean enabled,
                                     int priority, Instant updatedAt) {
        this.userId = userId;
        this.channel = channel;
        this.address = address;
        this.enabled = enabled;
        this.priority = priority;
        this.updatedAt = updatedAt;
    }

    public String getChannel() {
        return channel;
    }

    public String getAddress() {
        return address;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getPriority() {
        return priority;
    }
}
