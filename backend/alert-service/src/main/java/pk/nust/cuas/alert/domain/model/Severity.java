package pk.nust.cuas.alert.domain.model;

/**
 * An alert's severity (ARCHITECTURE.md sections 4.1.8, 6.3.4).
 *
 * <p>The same four-value scale threat-assessment produces, and deliberately so: an alert's severity
 * <em>is</em> the threat level that raised it, and a parallel vocabulary would be a second source of
 * truth for one fact that would diverge the first time a section 6.3.4 threshold moved.
 *
 * <p>The domain declares its own enum rather than importing {@code threat.v1.ThreatLevel} because
 * {@code domain/} compiles without Protobuf on the classpath (CLAUDE.md section 2); the two are
 * mapped at the boundary by {@code common/AlertProtoMapper}, exactly as track-correlation maps
 * {@code SensorModality}.
 *
 * <p>There is no {@code UNKNOWN} member. An alert is raised <em>from</em> an assessed level, so a
 * severity always exists — a track nothing has judged never produces one.
 */
public enum Severity {

    LOW,
    MEDIUM,
    HIGH,
    CRITICAL;

    /**
     * @param other the severity to compare against
     * @return {@code true} when this severity is greater than {@code other}
     */
    public boolean isAbove(Severity other) {
        return ordinal() > other.ordinal();
    }

    /**
     * @param other the severity to compare against
     * @return {@code true} when this severity is at least as great as {@code other}
     */
    public boolean isAtLeast(Severity other) {
        return ordinal() >= other.ordinal();
    }
}
