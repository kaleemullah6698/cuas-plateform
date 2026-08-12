package pk.nust.cuas.commons.bootstrap;

import java.util.UUID;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * The first administrator, created at startup so a fresh deployment can be signed into
 * (ARCHITECTURE.md sections 4.1.2, 4.1.3; added at M11).
 *
 * <p>Until M11 there was no way in. RBAC reference data seeded itself from the {@code Role} and
 * {@code Permission} enums, but no <em>user</em> existed, no endpoint creates a credential, and
 * every administrative endpoint needs a permission — so the platform could be started and could not
 * be used. This closes that, and it is the only code in the platform that creates a user without an
 * authenticated actor behind it.
 *
 * <p><strong>Shared between two services that own separate databases.</strong> auth-service owns
 * the credential in {@code authdb}; user-service owns the identity and its roles in {@code userdb}.
 * Neither may write to the other (database-per-service is absolute), so each seeds its own half and
 * the two agree by both being handed the same {@code userId} and {@code username}. The id is
 * configuration rather than generated precisely so the halves match without a call between them.
 *
 * <p><strong>Four rules this bootstrap follows, each because the obvious alternative is a
 * vulnerability:</strong>
 * <ul>
 *   <li><b>Off by default.</b> A production deployment that inherits this configuration
 *       accidentally must not grow an administrator nobody asked for.</li>
 *   <li><b>No default password, and startup fails without one.</b> A built-in default is a
 *       credential in source control (section 7.7) and would be the first thing tried against any
 *       deployment of this platform.</li>
 *   <li><b>Idempotent, and never overwrites.</b> If the administrator already exists the runner
 *       does nothing — it does not reset the password. Otherwise anyone able to restart a container
 *       could set the administrator's password to one they know.</li>
 *   <li><b>Audited.</b> Creating an ADMIN is the most privileged mutation the platform performs;
 *       section 7.5 requires it in the tamper-evident log like any other.</li>
 * </ul>
 *
 * @param enabled     whether to seed at all
 * @param userId      the shared identity, so both halves refer to the same person
 * @param username    the login name
 * @param email       the address recorded on the identity
 * @param displayName what an operator sees
 * @param password    the initial password; required when enabled, and never defaulted
 */
@ConfigurationProperties(prefix = "cuas.bootstrap.admin")
public record BootstrapAdminProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("00000000-0000-4000-8000-000000000001") UUID userId,
        @DefaultValue("admin") String username,
        @DefaultValue("admin@cuas.local") String email,
        @DefaultValue("Platform Administrator") String displayName,
        String password) {

    /** Minimum length for the initial password; section 7.3's policy floor. */
    private static final int MIN_PASSWORD_LENGTH = 12;

    /**
     * Fails startup when seeding is enabled but no usable password was supplied.
     *
     * <p>Deliberately a startup failure rather than a warning-and-skip. A deployment that asked for
     * an administrator and silently did not get one looks identical to a working one until somebody
     * tries to sign in, and the natural next step is to add a password to a file and commit it.
     *
     * @throws IllegalStateException when enabled without a password of sufficient length
     */
    public void requireUsablePassword() {
        if (!enabled) {
            return;
        }
        if (password == null || password.isBlank()) {
            throw new IllegalStateException(
                    "cuas.bootstrap.admin.enabled is true but no password was supplied. Set "
                            + "CUAS_BOOTSTRAP_ADMIN_PASSWORD in the environment. There is "
                            + "deliberately no default: a built-in one would be a credential in "
                            + "source control (ARCHITECTURE.md section 7.7).");
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException(
                    "The bootstrap administrator password must be at least " + MIN_PASSWORD_LENGTH
                            + " characters (ARCHITECTURE.md section 7.3).");
        }
    }
}
