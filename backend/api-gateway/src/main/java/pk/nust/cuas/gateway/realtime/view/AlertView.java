package pk.nust.cuas.gateway.realtime.view;

/**
 * The projection of an alert for the operator console (ARCHITECTURE.md §2.5.3, §3.5). Carried in the
 * {@code ALERT} message, never coalesced — every alert reaches the console on arrival.
 *
 * @param alertId   the alert
 * @param reference the operator-facing handle, e.g. "ALT-9912"
 * @param severity  the threat level (contract enum name)
 * @param trackId   the track it concerns
 * @param title     one-line operator-facing title
 * @param summary   the operator-facing rationale
 * @param raisedAt  when it was raised (ISO-8601)
 */
public record AlertView(String alertId, String reference, String severity, String trackId,
                        String title, String summary, String raisedAt) {
}
