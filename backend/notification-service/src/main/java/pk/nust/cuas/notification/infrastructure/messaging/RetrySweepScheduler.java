package pk.nust.cuas.notification.infrastructure.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pk.nust.cuas.notification.application.NotificationRetryService;

/**
 * Runs the retry sweep on a fixed interval (ARCHITECTURE.md section 4.1.9).
 *
 * <p>The trigger, kept apart from the sweep so {@link NotificationRetryService} can be driven directly
 * in a test without waiting on a scheduler. <strong>No leader election.</strong> Unlike
 * alert-service's Redis-scheduled escalation sweep, the claim itself is safe to run everywhere at
 * once: {@code findDueForRetry} locks each due row {@code FOR UPDATE SKIP LOCKED} and leases it out of
 * the way, so concurrent instances partition the work between them without coordinating.
 *
 * <p>Disable with {@code notification.retry.enabled=false}. The integration tests that drive the sweep
 * directly turn it off so a background pass cannot re-deliver out from under an assertion.
 */
@Component
@ConditionalOnProperty(name = "notification.retry.enabled", havingValue = "true", matchIfMissing = true)
public class RetrySweepScheduler {

    private static final Logger log = LoggerFactory.getLogger(RetrySweepScheduler.class);

    private final NotificationRetryService retryService;

    public RetrySweepScheduler(NotificationRetryService retryService) {
        this.retryService = retryService;
    }

    @Scheduled(fixedDelayString = "${notification.retry.check-interval:PT15S}")
    public void run() {
        try {
            retryService.sweep();
        } catch (RuntimeException ex) {
            // A scheduled method that throws is silently rescheduled by Spring, so an unlogged failure
            // here would quietly stop every retry with no symptom.
            log.error("Retry sweep failed; it will run again at the next interval", ex);
        }
    }
}
