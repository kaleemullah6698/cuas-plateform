package pk.nust.cuas.commons.audit;

/**
 * Action names for the events section 7.5 lists as mandatory.
 *
 * <p>The {@code action} column is free text so services can name domain-specific actions, but the
 * platform-wide ones are declared here so that a query for "every failed authentication" matches
 * one spelling rather than three. Names are {@code UPPER_SNAKE_CASE} and read as completed facts
 * ({@code LOGIN_SUCCEEDED}, not {@code LOGIN}).
 */
public final class AuditActions {

    // ── Authentication (section 7.3) ──
    public static final String LOGIN_SUCCEEDED = "LOGIN_SUCCEEDED";
    public static final String LOGIN_FAILED = "LOGIN_FAILED";
    public static final String MFA_CHALLENGE_ISSUED = "MFA_CHALLENGE_ISSUED";
    public static final String MFA_VERIFICATION_SUCCEEDED = "MFA_VERIFICATION_SUCCEEDED";
    public static final String MFA_VERIFICATION_FAILED = "MFA_VERIFICATION_FAILED";
    public static final String ACCOUNT_LOCKED = "ACCOUNT_LOCKED";
    public static final String LOGOUT = "LOGOUT";
    public static final String TOKEN_REFRESHED = "TOKEN_REFRESHED";
    public static final String TOKEN_REVOKED = "TOKEN_REVOKED";
    /** Refresh-token reuse detected; the whole family is revoked (section 7.3). */
    public static final String TOKEN_REUSE_DETECTED = "TOKEN_REUSE_DETECTED";

    // ── Identity and authorisation (section 7.4) ──
    public static final String USER_CREATED = "USER_CREATED";
    public static final String USER_UPDATED = "USER_UPDATED";
    public static final String USER_DEACTIVATED = "USER_DEACTIVATED";
    public static final String ROLE_ASSIGNED = "ROLE_ASSIGNED";
    public static final String ROLE_REVOKED = "ROLE_REVOKED";
    public static final String ROLE_PERMISSIONS_CHANGED = "ROLE_PERMISSIONS_CHANGED";
    public static final String SELF_ESCALATION_DENIED = "SELF_ESCALATION_DENIED";

    // ── Audit of the audit trail itself (section 7.5) ──
    public static final String AUDIT_QUERIED = "AUDIT_QUERIED";
    public static final String AUDIT_CHAIN_VERIFIED = "AUDIT_CHAIN_VERIFIED";
    public static final String AUDIT_CHAIN_BROKEN = "AUDIT_CHAIN_BROKEN";
    public static final String AUDIT_EXPORTED = "AUDIT_EXPORTED";

    private AuditActions() {
    }
}
