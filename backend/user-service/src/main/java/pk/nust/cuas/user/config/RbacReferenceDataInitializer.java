package pk.nust.cuas.user.config;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pk.nust.cuas.commons.security.Permission;
import pk.nust.cuas.commons.security.Role;

/**
 * Synchronises the {@code roles}, {@code permissions} and {@code role_permissions} reference
 * tables from the compile-time {@link Role} and {@link Permission} enums.
 *
 * <p>Section 7.4 declares the authorisation matrix closed: adding a permission is an architecture
 * change. The enums are therefore the single source of truth, and these tables exist only so
 * {@code user_roles} has referential integrity and an administrator can list what exists.
 * Seeding them from SQL in a migration would duplicate the matrix in a second place, and the two
 * copies would eventually disagree — with the database copy silently winning any foreign key.
 *
 * <p>The synchronisation is idempotent and additive. Rows are never deleted here: a role still
 * referenced by {@code user_roles} cannot be removed without deciding what happens to its holders,
 * which is a migration-time decision, not a startup one. A stale row is reported instead.
 */
@Component
public class RbacReferenceDataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RbacReferenceDataInitializer.class);

    private final JdbcTemplate jdbc;

    public RbacReferenceDataInitializer(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        upsertPermissions();
        upsertRoles();
        upsertRolePermissions();
        warnAboutStaleRows();
        log.info("RBAC reference data synchronised: {} roles, {} permissions",
                Role.values().length, Permission.values().length);
    }

    private void upsertPermissions() {
        for (Permission permission : Permission.values()) {
            jdbc.update("""
                    INSERT INTO permissions (code, description) VALUES (?, ?)
                    ON CONFLICT (code) DO UPDATE SET description = EXCLUDED.description
                    """, permission.authority(), permission.name());
        }
    }

    private void upsertRoles() {
        for (Role role : Role.values()) {
            jdbc.update("""
                    INSERT INTO roles (code, description) VALUES (?, ?)
                    ON CONFLICT (code) DO UPDATE SET description = EXCLUDED.description
                    """, role.name(), role.name() + " (" + role.permissions().size() + " permissions)");
        }
    }

    private void upsertRolePermissions() {
        // Rewritten wholesale per role: the matrix is the truth, so a grant removed from the enum
        // must disappear from the table too, or the database would still describe authority the
        // code no longer confers.
        for (Role role : Role.values()) {
            jdbc.update("DELETE FROM role_permissions WHERE role_code = ?", role.name());
            for (Permission permission : role.permissions()) {
                jdbc.update("""
                        INSERT INTO role_permissions (role_code, permission_code) VALUES (?, ?)
                        ON CONFLICT DO NOTHING
                        """, role.name(), permission.authority());
            }
        }
    }

    /**
     * Reports rows the enums no longer define. Deleting them automatically could orphan a user's
     * role assignment, so this surfaces the discrepancy for a human to resolve deliberately.
     */
    private void warnAboutStaleRows() {
        Set<String> knownRoles = Arrays.stream(Role.values()).map(Enum::name)
                .collect(Collectors.toSet());
        List<String> staleRoles = jdbc.queryForList("SELECT code FROM roles", String.class).stream()
                .filter(code -> !knownRoles.contains(code))
                .toList();

        if (!staleRoles.isEmpty()) {
            log.warn("Database defines roles this build does not: {}. "
                    + "Resolve with a migration; user_roles may still reference them.", staleRoles);
        }
    }
}
