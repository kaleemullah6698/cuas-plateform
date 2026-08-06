package pk.nust.cuas.alert.domain.model;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * How an alert ended (ARCHITECTURE.md section 4.1.8).
 *
 * <p><strong>{@code autoResolved} is not cosmetic.</strong> An alert closed because its track was
 * lost or merged is not evidence that anybody looked at it, and the false-positive rate reporting
 * computes from dispositions would be wrong if the two were pooled. An auto-resolution also has no
 * actor, which is why {@code actorId} is optional here and mandatory on {@link Acknowledgement}.
 *
 * @param alertId      the alert
 * @param disposition  the verdict
 * @param note         optional free text
 * @param actorId      the resolving operator, or null when the system closed it
 * @param actorName    the display name
 * @param autoResolved whether the system closed it rather than a person
 * @param occurredAt   platform clock
 */
public record Resolution(
        UUID alertId, AlertDisposition disposition, String note, String actorId, String actorName,
        boolean autoResolved, Instant occurredAt) {

    public Resolution {
        if (alertId == null || disposition == null || occurredAt == null) {
            throw new IllegalArgumentException(
                    "A resolution needs an alert, a disposition and a time");
        }
        if (!autoResolved && (actorId == null || actorId.isBlank())) {
            throw new IllegalArgumentException(
                    "An operator resolution must name its actor; only an auto-resolution has none");
        }
        note = note == null ? "" : note;
        actorName = actorName == null ? "" : actorName;
    }

    /**
     * An alert closed by the system because the thing it described no longer exists.
     *
     * <p>Always {@link AlertDisposition#INCONCLUSIVE}. The system knows the track ended; it does not
     * know whether the alert had been right, and recording a verdict it cannot support would poison
     * the calibration statistic that dispositions exist to produce.
     *
     * @param alertId    the alert
     * @param reason     why, for the note — for example "track lost"
     * @param occurredAt platform clock
     * @return the resolution
     */
    public static Resolution automatic(UUID alertId, String reason, Instant occurredAt) {
        return new Resolution(alertId, AlertDisposition.INCONCLUSIVE, reason, null, "",
                true, occurredAt);
    }

    /** @return the resolving operator, or empty when the system closed it */
    public Optional<String> actor() {
        return Optional.ofNullable(actorId);
    }
}
