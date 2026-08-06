package pk.nust.cuas.notification.domain.service;

import java.time.Duration;
import java.util.Optional;
import pk.nust.cuas.notification.domain.model.DeliveryFailureReason;

/**
 * Decides whether and when to retry a failed delivery (section 4.1.9, "retry with backoff").
 *
 * <p>A terminal failure — an unknown recipient, a disabled channel, a rejected message — is never
 * retried; retrying it would only spend the per-channel rate budget a genuinely retryable failure
 * needs. A retryable failure backs off exponentially from a base delay, doubling each attempt up to a
 * cap, and gives up after a maximum number of attempts. Pure and deterministic.
 */
public final class RetryPolicy {

    private final int maxAttempts;
    private final Duration baseBackoff;
    private final Duration maxBackoff;

    /**
     * @param maxAttempts the total number of attempts allowed, including the first
     * @param baseBackoff the delay after the first failure
     * @param maxBackoff  the cap the exponential backoff never exceeds
     */
    public RetryPolicy(int maxAttempts, Duration baseBackoff, Duration maxBackoff) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be at least 1");
        }
        if (baseBackoff.isNegative() || baseBackoff.isZero() || maxBackoff.compareTo(baseBackoff) < 0) {
            throw new IllegalArgumentException("backoff must be positive and the cap at least the base");
        }
        this.maxAttempts = maxAttempts;
        this.baseBackoff = baseBackoff;
        this.maxBackoff = maxBackoff;
    }

    /**
     * @param reason           why the attempt failed
     * @param attemptsSoFar    how many attempts have been made, including the one that just failed
     * @return the delay before the next attempt, or empty when no retry should happen
     */
    public Optional<Duration> nextBackoff(DeliveryFailureReason reason, int attemptsSoFar) {
        if (!reason.isRetryable() || attemptsSoFar >= maxAttempts) {
            return Optional.empty();
        }
        // attemptsSoFar is 1 after the first failure, so the first backoff is the base.
        long multiplier = 1L << Math.min(attemptsSoFar - 1, 30);
        Duration backoff = baseBackoff.multipliedBy(multiplier);
        return Optional.of(backoff.compareTo(maxBackoff) > 0 ? maxBackoff : backoff);
    }
}
