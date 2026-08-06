package pk.nust.cuas.notification.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

/** JPA mapping for {@code user_preferences} — a user's quiet hours and minimum severity. */
@Entity
@Table(name = "user_preferences")
public class UserPreferencesEntity {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "quiet_hours_start")
    private LocalTime quietHoursStart;

    @Column(name = "quiet_hours_end")
    private LocalTime quietHoursEnd;

    @Column(name = "min_severity", nullable = false)
    private String minSeverity;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserPreferencesEntity() {
    }

    public UserPreferencesEntity(UUID userId, LocalTime quietHoursStart, LocalTime quietHoursEnd,
                                 String minSeverity, Instant updatedAt) {
        this.userId = userId;
        this.quietHoursStart = quietHoursStart;
        this.quietHoursEnd = quietHoursEnd;
        this.minSeverity = minSeverity;
        this.updatedAt = updatedAt;
    }

    public void apply(LocalTime quietHoursStart, LocalTime quietHoursEnd, String minSeverity,
                      Instant updatedAt) {
        this.quietHoursStart = quietHoursStart;
        this.quietHoursEnd = quietHoursEnd;
        this.minSeverity = minSeverity;
        this.updatedAt = updatedAt;
    }

    public UUID getUserId() {
        return userId;
    }

    public LocalTime getQuietHoursStart() {
        return quietHoursStart;
    }

    public LocalTime getQuietHoursEnd() {
        return quietHoursEnd;
    }

    public String getMinSeverity() {
        return minSeverity;
    }
}
