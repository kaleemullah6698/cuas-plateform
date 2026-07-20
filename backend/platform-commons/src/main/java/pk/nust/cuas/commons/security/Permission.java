package pk.nust.cuas.commons.security;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The platform's complete permission vocabulary (ARCHITECTURE.md section 7.4).
 *
 * <p>Permissions are the atomic unit of authorisation; a {@link Role} is a bag of them. The
 * {@link #authority()} string is what appears in the JWT {@code perms} claim and in
 * {@code @PreAuthorize} expressions, so the two can never drift apart — a typo in an annotation
 * fails to match a constant here rather than silently granting or denying access.
 *
 * <p>This enum is closed by design. Adding a permission is an architecture change (section 7.4),
 * not a code change made in passing.
 */
public enum Permission {

    TRACK_READ("track:read"),
    TRACK_CLASSIFY("track:classify"),

    MAP_READ("map:read"),

    ALERT_READ("alert:read"),
    ALERT_ACKNOWLEDGE("alert:acknowledge"),
    ALERT_RESOLVE("alert:resolve"),

    INCIDENT_CREATE("incident:create"),
    INCIDENT_READ("incident:read"),
    INCIDENT_UPDATE("incident:update"),
    INCIDENT_CLOSE("incident:close"),

    EVIDENCE_EXPORT("evidence:export"),

    SENSOR_READ("sensor:read"),
    SENSOR_ACKNOWLEDGE_FAULT("sensor:acknowledge_fault"),
    SENSOR_STANDBY("sensor:standby"),
    SENSOR_CONFIGURE("sensor:configure"),

    REPORT_READ("report:read"),
    REPORT_GENERATE("report:generate"),

    AUDIT_READ("audit:read"),
    AUDIT_VERIFY("audit:verify"),
    AUDIT_EXPORT("audit:export"),

    POLICY_READ("policy:read"),
    POLICY_WRITE("policy:write"),

    AI_CONFIGURE("ai:configure"),

    USER_READ("user:read"),
    USER_WRITE("user:write"),
    ROLE_WRITE("role:write"),

    CONFIG_WRITE("config:write");

    private static final Map<String, Permission> BY_AUTHORITY =
            Arrays.stream(values()).collect(Collectors.toUnmodifiableMap(
                    Permission::authority, Function.identity()));

    private final String authority;

    Permission(String authority) {
        this.authority = authority;
    }

    /**
     * @return the wire form of this permission, as it appears in a JWT and in
     *         {@code @PreAuthorize("hasAuthority('…')")}
     */
    public String authority() {
        return authority;
    }

    /**
     * Resolves a permission from its wire form.
     *
     * @param authority the wire form, for example {@code "audit:read"}
     * @return the matching permission, or empty when the string is not a known permission
     */
    public static Optional<Permission> fromAuthority(String authority) {
        return Optional.ofNullable(BY_AUTHORITY.get(authority));
    }
}
