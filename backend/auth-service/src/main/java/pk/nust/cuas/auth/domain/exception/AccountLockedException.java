package pk.nust.cuas.auth.domain.exception;

import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import pk.nust.cuas.commons.exception.DomainException;

/**
 * The account is temporarily locked after repeated failures (ARCHITECTURE.md section 7.8).
 *
 * <p>The unlock time is disclosed: it tells a legitimate user when to retry, and tells an attacker
 * only that the lockout they already triggered is in force.
 */
public class AccountLockedException extends DomainException {

    public static final String CODE = "ACCOUNT_LOCKED";

    public AccountLockedException(Instant until) {
        super(CODE, HttpStatus.LOCKED,
                "Too many failed attempts. Try again after " + until,
                Map.of("lockedUntil", until.toString()), null);
    }
}
