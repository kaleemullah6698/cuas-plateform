package pk.nust.cuas.alert.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * An operator's note on an investigation (ARCHITECTURE.md section 4.1.8, {@code incident_notes}).
 *
 * <p>Append-only and attributed. A note is part of the investigative record section 2.4.3 exports as
 * evidence, so it names its author and is never edited — an editable note would let the account of
 * an incident change after the decisions it justified had already been taken.
 *
 * @param noteId     UUIDv7
 * @param incidentId the investigation
 * @param authorId   the user id
 * @param authorName their display name
 * @param body       the note
 * @param createdAt  platform clock
 */
public record IncidentNote(
        UUID noteId, UUID incidentId, String authorId, String authorName, String body,
        Instant createdAt) {

    public IncidentNote {
        if (noteId == null || incidentId == null || createdAt == null) {
            throw new IllegalArgumentException("A note needs an id, an incident and a time");
        }
        if (authorId == null || authorId.isBlank()) {
            throw new IllegalArgumentException(
                    "A note must name its author — it forms part of the exported investigative"
                            + " record, and an anonymous entry there is not evidence");
        }
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("A note must say something");
        }
        authorName = authorName == null ? "" : authorName;
    }
}
