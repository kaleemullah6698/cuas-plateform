package pk.nust.cuas.track.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA mapping for a {@code track_lifecycle_events} row: one status transition. The table is
 * append-only at the database (rules reject update and delete), so this entity is only ever inserted.
 */
@Entity
@Table(name = "track_lifecycle_events")
public class TrackLifecycleEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "track_id", nullable = false)
    private UUID trackId;

    @Column(name = "from_status")
    private String fromStatus;

    @Column(name = "to_status", nullable = false)
    private String toStatus;

    @Column(name = "reason", nullable = false)
    private String reason;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected TrackLifecycleEventEntity() {
    }

    public TrackLifecycleEventEntity(UUID trackId, String fromStatus, String toStatus, String reason,
                                     Instant occurredAt) {
        this.trackId = trackId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.reason = reason;
        this.occurredAt = occurredAt;
    }
}
