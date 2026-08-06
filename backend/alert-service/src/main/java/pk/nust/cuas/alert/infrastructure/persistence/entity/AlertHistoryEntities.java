package pk.nust.cuas.alert.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * The three append-only lifecycle history tables.
 *
 * <p>Grouped in one file because they are the same shape and are only ever read together — three
 * files of twenty lines each would spread one idea across a package for no gain. Each is still its
 * own {@code @Entity} and its own table.
 */
public final class AlertHistoryEntities {

    private AlertHistoryEntities() {
    }

    /** JPA mapping for an {@code alert_acknowledgements} row. */
    @Entity
    @Table(name = "alert_acknowledgements")
    public static class AcknowledgementEntity {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        @Column(name = "id")
        private Long id;

        @Column(name = "alert_id", nullable = false)
        private UUID alertId;

        @Column(name = "actor_id", nullable = false)
        private String actorId;

        @Column(name = "actor_name", nullable = false)
        private String actorName;

        @Column(name = "note", nullable = false)
        private String note;

        @Column(name = "occurred_at", nullable = false)
        private Instant occurredAt;

        protected AcknowledgementEntity() {
        }

        public AcknowledgementEntity(UUID alertId, String actorId, String actorName, String note,
                                     Instant occurredAt) {
            this.alertId = alertId;
            this.actorId = actorId;
            this.actorName = actorName;
            this.note = note;
            this.occurredAt = occurredAt;
        }

        public UUID getAlertId() {
            return alertId;
        }

        public String getActorId() {
            return actorId;
        }

        public String getActorName() {
            return actorName;
        }

        public String getNote() {
            return note;
        }

        public Instant getOccurredAt() {
            return occurredAt;
        }
    }

    /** JPA mapping for an {@code alert_escalations} row. */
    @Entity
    @Table(name = "alert_escalations")
    public static class EscalationEntity {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        @Column(name = "id")
        private Long id;

        @Column(name = "alert_id", nullable = false)
        private UUID alertId;

        @Column(name = "previous_level", nullable = false)
        private int previousLevel;

        @Column(name = "current_level", nullable = false)
        private int currentLevel;

        @Column(name = "trigger", nullable = false)
        private String trigger;

        @Column(name = "actor_id")
        private String actorId;

        @Column(name = "occurred_at", nullable = false)
        private Instant occurredAt;

        @Column(name = "next_escalates_at")
        private Instant nextEscalatesAt;

        protected EscalationEntity() {
        }

        @SuppressWarnings("checkstyle:ParameterNumber")
        public EscalationEntity(UUID alertId, int previousLevel, int currentLevel, String trigger,
                                String actorId, Instant occurredAt, Instant nextEscalatesAt) {
            this.alertId = alertId;
            this.previousLevel = previousLevel;
            this.currentLevel = currentLevel;
            this.trigger = trigger;
            this.actorId = actorId;
            this.occurredAt = occurredAt;
            this.nextEscalatesAt = nextEscalatesAt;
        }

        public UUID getAlertId() {
            return alertId;
        }

        public int getPreviousLevel() {
            return previousLevel;
        }

        public int getCurrentLevel() {
            return currentLevel;
        }

        public String getTrigger() {
            return trigger;
        }

        public String getActorId() {
            return actorId;
        }

        public Instant getOccurredAt() {
            return occurredAt;
        }

        public Instant getNextEscalatesAt() {
            return nextEscalatesAt;
        }
    }

    /** JPA mapping for an {@code alert_resolutions} row. */
    @Entity
    @Table(name = "alert_resolutions")
    public static class ResolutionEntity {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        @Column(name = "id")
        private Long id;

        @Column(name = "alert_id", nullable = false)
        private UUID alertId;

        @Column(name = "disposition", nullable = false)
        private String disposition;

        @Column(name = "note", nullable = false)
        private String note;

        @Column(name = "actor_id")
        private String actorId;

        @Column(name = "actor_name", nullable = false)
        private String actorName;

        @Column(name = "auto_resolved", nullable = false)
        private boolean autoResolved;

        @Column(name = "occurred_at", nullable = false)
        private Instant occurredAt;

        protected ResolutionEntity() {
        }

        @SuppressWarnings("checkstyle:ParameterNumber")
        public ResolutionEntity(UUID alertId, String disposition, String note, String actorId,
                                String actorName, boolean autoResolved, Instant occurredAt) {
            this.alertId = alertId;
            this.disposition = disposition;
            this.note = note;
            this.actorId = actorId;
            this.actorName = actorName;
            this.autoResolved = autoResolved;
            this.occurredAt = occurredAt;
        }

        public UUID getAlertId() {
            return alertId;
        }

        public String getDisposition() {
            return disposition;
        }

        public String getNote() {
            return note;
        }

        public String getActorId() {
            return actorId;
        }

        public String getActorName() {
            return actorName;
        }

        public boolean isAutoResolved() {
            return autoResolved;
        }

        public Instant getOccurredAt() {
            return occurredAt;
        }
    }
}
