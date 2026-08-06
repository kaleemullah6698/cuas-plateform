package pk.nust.cuas.notification.domain.model;

import java.util.Optional;
import java.util.UUID;

/**
 * What a notification is about, as references into the services that own the subjects. A notification
 * carries the handles an operator needs to open the alert, incident or track it concerns — never the
 * subject's own content, which lives on its own topic.
 *
 * @param alertId    the alert that triggered it, if any
 * @param incidentId the incident that triggered it, if any
 * @param trackId    the track it concerns, if any
 */
public record SubjectRef(Optional<UUID> alertId, Optional<UUID> incidentId, Optional<UUID> trackId) {

    public SubjectRef {
        if (alertId == null || incidentId == null || trackId == null) {
            throw new IllegalArgumentException("references must not be null; use Optional.empty()");
        }
    }

    public static SubjectRef none() {
        return new SubjectRef(Optional.empty(), Optional.empty(), Optional.empty());
    }

    public static SubjectRef ofAlert(UUID alertId, UUID trackId) {
        return new SubjectRef(Optional.of(alertId), Optional.empty(), Optional.ofNullable(trackId));
    }

    public static SubjectRef ofIncident(UUID incidentId, UUID trackId) {
        return new SubjectRef(Optional.empty(), Optional.of(incidentId), Optional.ofNullable(trackId));
    }
}
