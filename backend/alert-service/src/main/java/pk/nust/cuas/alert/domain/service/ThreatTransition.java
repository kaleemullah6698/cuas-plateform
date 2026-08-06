package pk.nust.cuas.alert.domain.service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import pk.nust.cuas.alert.domain.model.Recommendation;
import pk.nust.cuas.alert.domain.model.Severity;

/**
 * A threat level change, as this service understands it — the domain's own view of
 * {@code threat.level.changed.v1} (ARCHITECTURE.md sections 4.1.7, 4.1.8).
 *
 * <p>Declared here rather than using the generated contract type because {@code domain/} compiles
 * without Protobuf on the classpath (CLAUDE.md section 2); {@code common/AlertProtoMapper} maps the
 * two at the boundary.
 *
 * @param trackId        the track
 * @param sectorId       its sector
 * @param assessmentId   the assessment that produced the new level
 * @param previousLevel  the level before, or empty when this is the track's first assessed level
 * @param currentLevel   the level now
 * @param currentScore   the composite score
 * @param recommendation what threat-assessment advised
 * @param policyVersion  the scoring policy in force — part of the dedup key
 * @param changedAt      platform clock
 * @param correlationId  end-to-end trace
 */
public record ThreatTransition(
        UUID trackId,
        String sectorId,
        UUID assessmentId,
        Optional<Severity> previousLevel,
        Severity currentLevel,
        double currentScore,
        Recommendation recommendation,
        String policyVersion,
        Instant changedAt,
        String correlationId) {

    public ThreatTransition {
        if (trackId == null || currentLevel == null || changedAt == null) {
            throw new IllegalArgumentException(
                    "A transition needs a track, the level it moved to, and a time");
        }
        if (previousLevel == null || recommendation == null) {
            throw new IllegalArgumentException(
                    "A transition needs a previous level and a recommendation");
        }
        sectorId = sectorId == null ? "" : sectorId;
        policyVersion = policyVersion == null ? "" : policyVersion;
        correlationId = correlationId == null ? "" : correlationId;
    }

    /**
     * @return {@code true} when the level rose. A track's first assessed level counts as an
     *         escalation — it went from nothing to something.
     */
    public boolean isEscalation() {
        return previousLevel.map(currentLevel::isAbove).orElse(true);
    }
}
