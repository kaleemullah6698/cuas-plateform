package pk.nust.cuas.user.domain.model;

import java.time.Instant;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import pk.nust.cuas.commons.security.Permission;
import pk.nust.cuas.commons.security.Role;

/**
 * A platform user account (ARCHITECTURE.md section 4.1.3).
 *
 * <p>Owns identity and role membership only. Credentials, MFA secrets and sessions belong to
 * auth-service: this service decides <em>what a user may do</em>, never <em>whether they are who
 * they claim to be</em>. Keeping the two apart is what stops an authentication bug from becoming
 * an authorisation bug.
 *
 * <p>Mutation returns a new instance rather than modifying in place, so a caller cannot hold a
 * reference that changes underneath it, and an unsaved change cannot leak into another request.
 */
public final class User {

    private final UUID id;
    private final String username;
    private final String email;
    private final String displayName;
    private final UserStatus status;
    private final Set<Role> roles;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final long version;

    public User(UUID id, String username, String email, String displayName, UserStatus status,
                Set<Role> roles, Instant createdAt, Instant updatedAt, long version) {
        this.id = Objects.requireNonNull(id, "id");
        this.username = requireText(username, "username");
        this.email = requireText(email, "email");
        this.displayName = requireText(displayName, "displayName");
        this.status = Objects.requireNonNull(status, "status");
        this.roles = roles == null || roles.isEmpty()
                ? Collections.unmodifiableSet(EnumSet.noneOf(Role.class))
                : Collections.unmodifiableSet(EnumSet.copyOf(roles));
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.version = version;
    }

    /**
     * Creates a new active account with no roles.
     *
     * <p>A new account starts with no authority at all: roles are granted afterwards, through the
     * assignment path that enforces self-escalation and separation-of-duty checks. Seeding a
     * default role here would bypass those checks.
     */
    public static User create(String username, String email, String displayName) {
        return new User(UUID.randomUUID(), username, email, displayName, UserStatus.ACTIVE,
                Set.of(), null, null, 0L);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be null or blank");
        }
        return value;
    }

    public UUID id() { return id; }
    public String username() { return username; }
    public String email() { return email; }
    public String displayName() { return displayName; }
    public UserStatus status() { return status; }
    public Set<Role> roles() { return roles; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public long version() { return version; }

    /** @return {@code true} when the account may authenticate and hold authority */
    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    /**
     * The union of the permissions granted by every assigned role.
     *
     * <p>Section 7.4: permissions are additive and roles never inherit from one another, so the
     * effective set is a plain union with no precedence rules to get wrong.
     *
     * @return the effective permissions; empty for an account with no roles
     */
    public Set<Permission> effectivePermissions() {
        Set<Permission> effective = EnumSet.noneOf(Permission.class);
        for (Role role : roles) {
            effective.addAll(role.permissions());
        }
        return Collections.unmodifiableSet(effective);
    }

    /**
     * @param permission the permission to test
     * @return {@code true} when a role of this user grants it and the account is active
     */
    public boolean holds(Permission permission) {
        return isActive() && effectivePermissions().contains(permission);
    }

    /** @return a copy with the role added; unchanged when it is already held */
    public User withRole(Role role) {
        Set<Role> updated = new LinkedHashSet<>(roles);
        updated.add(role);
        return new User(id, username, email, displayName, status, updated,
                createdAt, updatedAt, version);
    }

    /** @return a copy with the role removed; unchanged when it is not held */
    public User withoutRole(Role role) {
        Set<Role> updated = new LinkedHashSet<>(roles);
        updated.remove(role);
        return new User(id, username, email, displayName, status, updated,
                createdAt, updatedAt, version);
    }

    /** @return a copy with updated profile fields */
    public User withProfile(String newEmail, String newDisplayName) {
        return new User(id, username,
                newEmail == null ? email : newEmail,
                newDisplayName == null ? displayName : newDisplayName,
                status, roles, createdAt, updatedAt, version);
    }

    /**
     * @return a deactivated copy; a deactivated account holds no authority even though its role
     *         assignments are retained, so reactivation does not silently restore nothing
     */
    public User deactivated() {
        return new User(id, username, email, displayName, UserStatus.DEACTIVATED, roles,
                createdAt, updatedAt, version);
    }

    /** @return a reactivated copy */
    public User reactivated() {
        return new User(id, username, email, displayName, UserStatus.ACTIVE, roles,
                createdAt, updatedAt, version);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof User user && id.equals(user.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        // Deliberately excludes email: user identifiers end up in logs, personal data should not.
        return "User[id=%s, username=%s, status=%s, roles=%s]".formatted(id, username, status, roles);
    }
}
