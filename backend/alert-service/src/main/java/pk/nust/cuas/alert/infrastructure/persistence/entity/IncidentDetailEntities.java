package pk.nust.cuas.alert.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * The three append-only tables that make up an investigation's record: its timeline, its notes and
 * its evidence bundle.
 *
 * <p>Grouped in one file for the same reason as {@link AlertHistoryEntities}: they share a shape and
 * a purpose, and they are always read together when section 2.4.3's investigation screen is
 * assembled. Each is still its own {@code @Entity} and its own table.
 *
 * <p>None of them has a mutator. They are the record section 2.4.3 exports as evidence, and a record
 * that could be rewritten after the decisions it justified were taken is not one a reviewer can
 * rely on.
 */
public final class IncidentDetailEntities {

    private IncidentDetailEntities() {
    }

    /** JPA mapping for an {@code incident_events} row: one line of the section 2.4.3 timeline. */
    @Entity
    @Table(name = "incident_events")
    public static class EventEntity {

        @Id
        @Column(name = "event_id")
        private UUID eventId;

        @Column(name = "incident_id", nullable = false)
        private UUID incidentId;

        @Column(name = "source_topic", nullable = false)
        private String sourceTopic;

        @Column(name = "source_id", nullable = false)
        private String sourceId;

        @Column(name = "summary", nullable = false)
        private String summary;

        /** Null for a system entry — what section 2.4.3 renders as {@code SYS} rather than {@code USER}. */
        @Column(name = "actor_id")
        private String actorId;

        @Column(name = "actor_name", nullable = false)
        private String actorName;

        @Column(name = "occurred_at", nullable = false)
        private Instant occurredAt;

        protected EventEntity() {
        }

        @SuppressWarnings("checkstyle:ParameterNumber")
        public EventEntity(UUID eventId, UUID incidentId, String sourceTopic, String sourceId,
                           String summary, String actorId, String actorName, Instant occurredAt) {
            this.eventId = eventId;
            this.incidentId = incidentId;
            this.sourceTopic = sourceTopic;
            this.sourceId = sourceId;
            this.summary = summary;
            this.actorId = actorId;
            this.actorName = actorName;
            this.occurredAt = occurredAt;
        }

        public UUID getEventId() {
            return eventId;
        }

        public UUID getIncidentId() {
            return incidentId;
        }

        public String getSourceTopic() {
            return sourceTopic;
        }

        public String getSourceId() {
            return sourceId;
        }

        public String getSummary() {
            return summary;
        }

        public String getActorId() {
            return actorId;
        }

        public String getActorName() {
            return actorName;
        }

        public Instant getOccurredAt() {
            return occurredAt;
        }
    }

    /** JPA mapping for an {@code incident_notes} row. */
    @Entity
    @Table(name = "incident_notes")
    public static class NoteEntity {

        @Id
        @Column(name = "note_id")
        private UUID noteId;

        @Column(name = "incident_id", nullable = false)
        private UUID incidentId;

        @Column(name = "author_id", nullable = false)
        private String authorId;

        @Column(name = "author_name", nullable = false)
        private String authorName;

        @Column(name = "body", nullable = false)
        private String body;

        @Column(name = "created_at", nullable = false)
        private Instant createdAt;

        protected NoteEntity() {
        }

        public NoteEntity(UUID noteId, UUID incidentId, String authorId, String authorName,
                          String body, Instant createdAt) {
            this.noteId = noteId;
            this.incidentId = incidentId;
            this.authorId = authorId;
            this.authorName = authorName;
            this.body = body;
            this.createdAt = createdAt;
        }

        public UUID getNoteId() {
            return noteId;
        }

        public UUID getIncidentId() {
            return incidentId;
        }

        public String getAuthorId() {
            return authorId;
        }

        public String getAuthorName() {
            return authorName;
        }

        public String getBody() {
            return body;
        }

        public Instant getCreatedAt() {
            return createdAt;
        }
    }

    /** JPA mapping for an {@code incident_evidence} row: a reference, never a payload. */
    @Entity
    @Table(name = "incident_evidence")
    public static class EvidenceEntity {

        @Id
        @Column(name = "evidence_id")
        private UUID evidenceId;

        @Column(name = "incident_id", nullable = false)
        private UUID incidentId;

        @Column(name = "kind", nullable = false)
        private String kind;

        @Column(name = "uri", nullable = false)
        private String uri;

        @Column(name = "sha256", nullable = false)
        private String sha256;

        @Column(name = "size_bytes", nullable = false)
        private long sizeBytes;

        @Column(name = "content_type", nullable = false)
        private String contentType;

        @Column(name = "source_id", nullable = false)
        private String sourceId;

        @Column(name = "captured_at", nullable = false)
        private Instant capturedAt;

        protected EvidenceEntity() {
        }

        @SuppressWarnings("checkstyle:ParameterNumber")
        public EvidenceEntity(UUID evidenceId, UUID incidentId, String kind, String uri,
                              String sha256, long sizeBytes, String contentType, String sourceId,
                              Instant capturedAt) {
            this.evidenceId = evidenceId;
            this.incidentId = incidentId;
            this.kind = kind;
            this.uri = uri;
            this.sha256 = sha256;
            this.sizeBytes = sizeBytes;
            this.contentType = contentType;
            this.sourceId = sourceId;
            this.capturedAt = capturedAt;
        }

        public UUID getEvidenceId() {
            return evidenceId;
        }

        public UUID getIncidentId() {
            return incidentId;
        }

        public String getKind() {
            return kind;
        }

        public String getUri() {
            return uri;
        }

        public String getSha256() {
            return sha256;
        }

        public long getSizeBytes() {
            return sizeBytes;
        }

        public String getContentType() {
            return contentType;
        }

        public String getSourceId() {
            return sourceId;
        }

        public Instant getCapturedAt() {
            return capturedAt;
        }
    }
}
