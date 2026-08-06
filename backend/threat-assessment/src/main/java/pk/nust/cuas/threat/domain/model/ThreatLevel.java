package pk.nust.cuas.threat.domain.model;

/**
 * The composite threat level (ARCHITECTURE.md section 6.3.4).
 *
 * <p>Ordered and closed. The ordering carries meaning — it is what an escalation is measured
 * against — so {@link #isAbove(ThreatLevel)} is provided rather than leaving callers to compare
 * {@link Enum#ordinal()} and risk getting the direction wrong.
 *
 * <p><strong>There is no {@code UNASSESSED} member, deliberately.</strong> A track nothing has been
 * able to score has <em>no</em> level, which the domain expresses as an absent
 * {@link ThreatAssessment#level()} rather than as a sixth constant. An enum member would be sortable
 * alongside the real levels, and the first {@code level <= LOW} filter written against it would let
 * an unjudged track read as the safest thing on the operator's screen.
 */
public enum ThreatLevel {

    LOW,
    MEDIUM,
    HIGH,
    CRITICAL;

    /**
     * @param other the level to compare against
     * @return {@code true} when this level is more severe than {@code other}
     */
    public boolean isAbove(ThreatLevel other) {
        return ordinal() > other.ordinal();
    }

    /**
     * @param other the level to compare against
     * @return {@code true} when this level is at least as severe as {@code other}
     */
    public boolean isAtLeast(ThreatLevel other) {
        return ordinal() >= other.ordinal();
    }
}
