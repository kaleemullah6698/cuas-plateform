package pk.nust.cuas.user.domain.policy;

import java.util.Set;
import java.util.UUID;
import pk.nust.cuas.commons.security.Permission;
import pk.nust.cuas.commons.security.Role;
import pk.nust.cuas.user.domain.exception.SelfEscalationDeniedException;
import pk.nust.cuas.user.domain.exception.SeparationOfDutyViolationException;
import pk.nust.cuas.user.domain.model.User;

/**
 * The rules governing who may grant what (ARCHITECTURE.md section 7.4).
 *
 * <p>Expressed as an explicit policy object rather than scattered {@code if} statements in a
 * service method, because these are the constraints a reviewer will want to read in one place —
 * and the ones an attacker will probe first.
 *
 * <p>Two independent constraints are enforced:
 * <ol>
 *   <li><strong>No self-escalation.</strong> An actor may not grant a permission they do not
 *       themselves hold. Without this, any account with {@code role:write} could promote itself
 *       to ADMIN, making every other permission boundary decorative.</li>
 *   <li><strong>Separation of duty.</strong> The role catalogue itself must not let one account
 *       both alter permissions and produce the authoritative record of having done so.</li>
 * </ol>
 */
public final class RoleAssignmentPolicy {

    /**
     * Checks that an actor may assign a role to a target user.
     *
     * @param actorId          the acting user's id, for the denial record
     * @param actorPermissions the permissions the actor actually holds
     * @param target           the user being changed
     * @param role             the role being granted
     * @throws SelfEscalationDeniedException      when the grant would widen the actor's own reach
     * @throws SeparationOfDutyViolationException when the resulting combination is forbidden
     */
    public void checkAssignable(UUID actorId, Set<Permission> actorPermissions,
                                User target, Role role) {
        checkNoSelfEscalation(actorId, actorPermissions, role);
        checkSeparationOfDuty(target, role);
    }

    /**
     * An actor may only grant permissions they already hold.
     *
     * <p>The check compares the <em>full</em> permission set of the role being granted, not just
     * the role name: granting a role is granting every permission inside it, so anything the actor
     * lacks is an escalation regardless of how it is packaged.
     */
    private void checkNoSelfEscalation(UUID actorId, Set<Permission> actorPermissions, Role role) {
        Set<Permission> granted = role.permissions();
        var escalating = granted.stream()
                .filter(permission -> !actorPermissions.contains(permission))
                .toList();

        if (!escalating.isEmpty()) {
            throw new SelfEscalationDeniedException(actorId, role, escalating);
        }
    }

    /**
     * Section 7.4 forbids one account holding both {@code role:write} and {@code audit:export}:
     * whoever can alter permissions must not be able to produce the signed record of the change.
     *
     * <p>The check is on the resulting permission set, not on role names, so the constraint cannot
     * be sidestepped by combining two roles that are individually acceptable.
     */
    private void checkSeparationOfDuty(User target, Role role) {
        Set<Permission> resulting = target.withRole(role).effectivePermissions();

        if (resulting.contains(Permission.ROLE_WRITE) && resulting.contains(Permission.AUDIT_EXPORT)) {
            throw new SeparationOfDutyViolationException(
                    "Assigning %s would grant both role:write and audit:export to user %s. "
                            .formatted(role, target.id())
                            + "Whoever can alter permissions must not be able to produce the "
                            + "authoritative record of having done so (ARCHITECTURE.md 7.4).");
        }
    }
}
