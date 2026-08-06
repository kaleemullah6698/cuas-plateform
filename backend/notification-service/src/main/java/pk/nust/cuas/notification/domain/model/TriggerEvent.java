package pk.nust.cuas.notification.domain.model;

/**
 * A source event to notify about, projected out of the alert, incident or health event that raised it
 * (section 4.1.9). What the domain works with, so it stays free of the generated protobuf types.
 *
 * <p>{@code dedupSeed} is the source event's own deduplication identity — an alert's dedup key, an
 * escalation's alert-and-level, an incident id. Combined with a recipient it becomes the notification
 * dedup key, so one recipient is notified once per source event however many channels or recurrences.
 *
 * @param trigger       what happened
 * @param urgency       how urgent it is
 * @param dedupSeed     the source event's deduplication identity
 * @param refs          the alert/incident/track it concerns
 * @param subject       one-line operator-facing text
 * @param correlationId the trace carried from the source event
 */
public record TriggerEvent(NotificationTrigger trigger, Urgency urgency, String dedupSeed,
                           SubjectRef refs, String subject, String correlationId) {

    public TriggerEvent {
        if (trigger == null || urgency == null || refs == null) {
            throw new IllegalArgumentException("trigger, urgency and refs are required");
        }
        if (dedupSeed == null || dedupSeed.isBlank()) {
            throw new IllegalArgumentException("dedupSeed is required");
        }
        subject = subject == null ? "" : subject;
        correlationId = correlationId == null ? "" : correlationId;
    }
}
