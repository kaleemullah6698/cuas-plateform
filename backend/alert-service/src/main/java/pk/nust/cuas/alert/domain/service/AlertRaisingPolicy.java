package pk.nust.cuas.alert.domain.service;

import pk.nust.cuas.alert.domain.model.Severity;

/**
 * Which threat transitions are worth an operator's attention (ARCHITECTURE.md section 4.1.8).
 *
 * <p>Not every level change is an alert. Two rules, and both are about what an alert queue is
 * <em>for</em>:
 *
 * <ul>
 *   <li><b>De-escalations raise nothing.</b> A track dropping from CRITICAL to HIGH is good news,
 *       and good news does not belong in a queue an operator works through. The open CRITICAL alert
 *       is still there and still needs resolving; adding a HIGH alert beside it would double the
 *       work for a situation that improved.</li>
 *   <li><b>A minimum severity.</b> Below it, a track is visible on the console and on
 *       {@code threat.assessed.v1} but does not interrupt anybody. The default is MEDIUM: LOW is
 *       where a bird and a distant permitted flight live, and an alert queue that fills with them is
 *       one nobody reads carefully by the time something matters.</li>
 * </ul>
 *
 * <p>Both are policy rather than truth, which is why they are a configured value object and not an
 * {@code if} in a consumer.
 *
 * @param minimumSeverity the lowest severity that raises an alert
 */
public record AlertRaisingPolicy(Severity minimumSeverity) {

    /** The default: MEDIUM and above interrupt somebody. */
    public static final AlertRaisingPolicy DEFAULT = new AlertRaisingPolicy(Severity.MEDIUM);

    public AlertRaisingPolicy {
        if (minimumSeverity == null) {
            throw new IllegalArgumentException("A raising policy needs a minimum severity");
        }
    }

    /**
     * @param transition the threat level change
     * @return {@code true} when this change should produce an alert
     */
    public boolean shouldRaise(ThreatTransition transition) {
        return transition.isEscalation()
                && transition.currentLevel().isAtLeast(minimumSeverity);
    }
}
