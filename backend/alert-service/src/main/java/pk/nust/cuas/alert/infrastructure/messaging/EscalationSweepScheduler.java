package pk.nust.cuas.alert.infrastructure.messaging;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pk.nust.cuas.alert.application.EscalationSweep;
import pk.nust.cuas.alert.domain.port.LeaderElection;

/**
 * Runs the escalation sweep on a fixed interval (ARCHITECTURE.md section 4.1.8).
 *
 * <p>The trigger, separated from the sweep itself so {@link EscalationSweep} can be exercised
 * directly in a test without waiting on a scheduler or a clock.
 *
 * <p><strong>The lease is released on shutdown.</strong> Without it, a rolling restart would leave
 * the new instance unable to lead until the old lease expired — a gap during which nothing sweeps,
 * and unacknowledged alerts sit at their deadlines.
 *
 * <p>Disable with {@code alert.sweep.enabled=false}. The integration tests that drive the sweep
 * directly turn it off so a background pass cannot escalate an alert out from under an assertion.
 */
@Component
@ConditionalOnProperty(name = "alert.sweep.enabled", havingValue = "true", matchIfMissing = true)
public class EscalationSweepScheduler {

    private static final Logger log = LoggerFactory.getLogger(EscalationSweepScheduler.class);

    private final EscalationSweep sweep;
    private final LeaderElection leader;

    public EscalationSweepScheduler(EscalationSweep sweep, LeaderElection leader) {
        this.sweep = sweep;
        this.leader = leader;
    }

    @Scheduled(fixedDelayString = "${alert.sweep.interval:PT15S}")
    public void run() {
        try {
            sweep.sweep();
        } catch (RuntimeException ex) {
            // A scheduled method that throws is silently rescheduled by Spring, so an unlogged
            // failure here would stop escalations with no symptom at all.
            log.error("Escalation sweep failed; it will run again at the next interval", ex);
        }
    }

    @PreDestroy
    public void releaseLeadership() {
        leader.release();
    }
}
