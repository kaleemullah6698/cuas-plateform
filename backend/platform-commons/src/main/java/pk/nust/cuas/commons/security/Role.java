package pk.nust.cuas.commons.security;

import static pk.nust.cuas.commons.security.Permission.AI_CONFIGURE;
import static pk.nust.cuas.commons.security.Permission.ALERT_ACKNOWLEDGE;
import static pk.nust.cuas.commons.security.Permission.ALERT_READ;
import static pk.nust.cuas.commons.security.Permission.ALERT_RESOLVE;
import static pk.nust.cuas.commons.security.Permission.AUDIT_EXPORT;
import static pk.nust.cuas.commons.security.Permission.AUDIT_READ;
import static pk.nust.cuas.commons.security.Permission.AUDIT_VERIFY;
import static pk.nust.cuas.commons.security.Permission.CONFIG_WRITE;
import static pk.nust.cuas.commons.security.Permission.EVIDENCE_EXPORT;
import static pk.nust.cuas.commons.security.Permission.INCIDENT_CLOSE;
import static pk.nust.cuas.commons.security.Permission.INCIDENT_CREATE;
import static pk.nust.cuas.commons.security.Permission.INCIDENT_READ;
import static pk.nust.cuas.commons.security.Permission.INCIDENT_UPDATE;
import static pk.nust.cuas.commons.security.Permission.MAP_READ;
import static pk.nust.cuas.commons.security.Permission.POLICY_READ;
import static pk.nust.cuas.commons.security.Permission.POLICY_WRITE;
import static pk.nust.cuas.commons.security.Permission.REPORT_GENERATE;
import static pk.nust.cuas.commons.security.Permission.REPORT_READ;
import static pk.nust.cuas.commons.security.Permission.ROLE_WRITE;
import static pk.nust.cuas.commons.security.Permission.SENSOR_ACKNOWLEDGE_FAULT;
import static pk.nust.cuas.commons.security.Permission.SENSOR_CONFIGURE;
import static pk.nust.cuas.commons.security.Permission.SENSOR_READ;
import static pk.nust.cuas.commons.security.Permission.SENSOR_STANDBY;
import static pk.nust.cuas.commons.security.Permission.TRACK_CLASSIFY;
import static pk.nust.cuas.commons.security.Permission.TRACK_READ;
import static pk.nust.cuas.commons.security.Permission.USER_READ;
import static pk.nust.cuas.commons.security.Permission.USER_WRITE;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * The platform's six roles and the permissions each grants (ARCHITECTURE.md section 7.4).
 *
 * <p>Permissions are <em>additive and explicit</em>: no role inherits from another, because
 * implicit inheritance is where privilege-escalation defects hide (section 7.4). Each role below
 * therefore lists its full permission set, even where that repeats another role's entries.
 *
 * <p><strong>Separation of duty.</strong> {@link #ADMIN} deliberately does <em>not</em> hold
 * {@link Permission#AUDIT_EXPORT}: the administrator who can alter permissions must not also be
 * able to produce the authoritative signed record of having done so. {@link #assertSeparationOfDuty()}
 * encodes that invariant so it cannot be quietly undone by a later edit.
 */
public enum Role {

    /** Read-only situational awareness. */
    VIEWER(EnumSet.of(
            TRACK_READ, MAP_READ, ALERT_READ, SENSOR_READ)),

    /** Works the alert queue and declares incidents. */
    OPERATOR(EnumSet.of(
            TRACK_READ, TRACK_CLASSIFY, MAP_READ,
            ALERT_READ, ALERT_ACKNOWLEDGE, ALERT_RESOLVE,
            INCIDENT_CREATE, INCIDENT_READ, INCIDENT_UPDATE,
            SENSOR_READ, REPORT_READ, POLICY_READ)),

    /** Everything the operator can do, plus closing incidents and producing reports. */
    ANALYST(EnumSet.of(
            TRACK_READ, TRACK_CLASSIFY, MAP_READ,
            ALERT_READ, ALERT_ACKNOWLEDGE, ALERT_RESOLVE,
            INCIDENT_CREATE, INCIDENT_READ, INCIDENT_UPDATE, INCIDENT_CLOSE,
            EVIDENCE_EXPORT, SENSOR_READ,
            REPORT_READ, REPORT_GENERATE, POLICY_READ)),

    /** Keeps the sensing and AI infrastructure running; no operational authority over alerts. */
    ENGINEER(EnumSet.of(
            TRACK_READ, MAP_READ, ALERT_READ, INCIDENT_READ,
            SENSOR_READ, SENSOR_ACKNOWLEDGE_FAULT, SENSOR_STANDBY, SENSOR_CONFIGURE,
            REPORT_READ, REPORT_GENERATE, POLICY_READ,
            AI_CONFIGURE,
            // Section 7.4 marks config:write as scoped for ENGINEER: sensor and AI namespaces
            // only. The namespace restriction is enforced by config-service at M8; the
            // permission itself is granted here.
            CONFIG_WRITE)),

    /** Reads and attests to the record. The only role permitted to export the audit log. */
    AUDITOR(EnumSet.of(
            TRACK_READ, MAP_READ, ALERT_READ, INCIDENT_READ,
            EVIDENCE_EXPORT, SENSOR_READ,
            REPORT_READ, REPORT_GENERATE,
            AUDIT_READ, AUDIT_VERIFY, AUDIT_EXPORT,
            POLICY_READ, USER_READ)),

    /** Administers the platform. Deliberately excludes {@code audit:export}. */
    ADMIN(EnumSet.of(
            TRACK_READ, TRACK_CLASSIFY, MAP_READ,
            ALERT_READ, ALERT_ACKNOWLEDGE, ALERT_RESOLVE,
            INCIDENT_CREATE, INCIDENT_READ, INCIDENT_UPDATE, INCIDENT_CLOSE,
            EVIDENCE_EXPORT,
            SENSOR_READ, SENSOR_ACKNOWLEDGE_FAULT, SENSOR_STANDBY, SENSOR_CONFIGURE,
            REPORT_READ, REPORT_GENERATE,
            AUDIT_READ, AUDIT_VERIFY,
            POLICY_READ, POLICY_WRITE,
            AI_CONFIGURE,
            USER_READ, USER_WRITE, ROLE_WRITE,
            CONFIG_WRITE));

    private final Set<Permission> permissions;

    Role(Set<Permission> permissions) {
        this.permissions = Collections.unmodifiableSet(permissions);
    }

    /** @return the permissions this role grants; never {@code null}, never modifiable */
    public Set<Permission> permissions() {
        return permissions;
    }

    /**
     * @param permission the permission to test
     * @return {@code true} when this role grants the permission
     */
    public boolean grants(Permission permission) {
        return permissions.contains(permission);
    }

    /**
     * Verifies the separation-of-duty invariants of section 7.4.
     *
     * <p>Invoked at application start so a violation fails the service immediately rather than
     * silently widening authority in production.
     *
     * @throws IllegalStateException when an invariant is violated
     */
    public static void assertSeparationOfDuty() {
        if (ADMIN.grants(AUDIT_EXPORT)) {
            throw new IllegalStateException(
                    "Separation of duty violated: ADMIN must not hold audit:export "
                            + "(ARCHITECTURE.md section 7.4)");
        }
        if (!AUDITOR.grants(AUDIT_EXPORT)) {
            throw new IllegalStateException(
                    "AUDITOR must hold audit:export (ARCHITECTURE.md section 7.4)");
        }
    }
}
