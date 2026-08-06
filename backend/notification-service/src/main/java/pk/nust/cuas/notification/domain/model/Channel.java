package pk.nust.cuas.notification.domain.model;

/**
 * A delivery channel (ARCHITECTURE.md section 4.1.9). Domain mirror of the {@code
 * NotificationChannel} contract enum. {@code WEBSOCKET} is in-console push via the gateway fan-out;
 * the others leave the platform.
 */
public enum Channel {
    WEBSOCKET,
    EMAIL,
    SMS,
    WEBHOOK
}
