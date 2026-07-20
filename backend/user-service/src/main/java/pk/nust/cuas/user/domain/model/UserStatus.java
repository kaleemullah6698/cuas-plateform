package pk.nust.cuas.user.domain.model;

/**
 * Lifecycle state of an account (ARCHITECTURE.md section 4.1.3).
 *
 * <p>Accounts are deactivated, never deleted: the audit trail references the actor that performed
 * each action, and a deleted account would leave those references dangling.
 */
public enum UserStatus {

    /** May authenticate and exercise the permissions of its roles. */
    ACTIVE,

    /** Retains its role assignments but holds no authority; sessions are revoked on transition. */
    DEACTIVATED
}
