package pk.nust.cuas.notification.domain.model;

/**
 * What a notification was about (ARCHITECTURE.md section 4.1.9). Domain mirror of the contract enum;
 * a stable vocabulary that per-user preferences are expressed against, rather than the versioned
 * source topic names.
 */
public enum NotificationTrigger {
    ALERT_RAISED,
    ALERT_ESCALATED,
    INCIDENT_CREATED,
    SENSOR_OFFLINE,
    TEST
}
