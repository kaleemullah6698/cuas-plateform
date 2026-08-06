package pk.nust.cuas.threat.domain.model;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * A change in a track's threat level, published on {@code threat.level.changed.v1}
 * (ARCHITECTURE.md sections 4.1.7, 5.3.3).
 *
 * <p><strong>Only produced when the level actually changes.</strong> Alert-service keys its
 * lifecycle off this topic (section 4.1.8), and a level republished unchanged at the assessment rate
 * would raise one alert per track update — the duplicate-alert failure section 5.7 names as the one
 * that destroys operator trust fastest.
 *
 * <p>{@code previousLevel} is absent for a track's first assessed level. That is a real distinction:
 * a track appearing at HIGH has never been anything else, and reporting a fabricated LOW as its
 * previous level would describe an escalation that never happened.
 *
 * @param trackId       the track
 * @param sectorId      its sector
 * @param assessmentId  the assessment that produced the new level
 * @param previousLevel the level before, or empty when this is the first assessed level
 * @param currentLevel  the level now
 * @param previousScore the composite before; zero when there was no previous assessment
 * @param currentScore  the composite now
 * @param recommendation the advice in force at the new level
 * @param changedAt     platform clock
 * @param correlationId end-to-end trace
 */
public record ThreatLevelTransition(
        UUID trackId,
        String sectorId,
        UUID assessmentId,
        Optional<ThreatLevel> previousLevel,
        ThreatLevel currentLevel,
        double previousScore,
        double currentScore,
        AdvisoryRecommendation recommendation,
        Instant changedAt,
        String correlationId) {

    public ThreatLevelTransition {
        if (trackId == null || assessmentId == null || currentLevel == null) {
            throw new IllegalArgumentException(
                    "A transition needs a track, the assessment behind it, and the level it moved to");
        }
        if (previousLevel == null || recommendation == null || changedAt == null) {
            throw new IllegalArgumentException(
                    "A transition needs a previous level, a recommendation and a time");
        }
        if (previousLevel.filter(currentLevel::equals).isPresent()) {
            throw new IllegalArgumentException(
                    "A transition must be a change; " + currentLevel + " did not move");
        }
        sectorId = sectorId == null ? "" : sectorId;
        correlationId = correlationId == null ? "" : correlationId;
    }

    /**
     * @return {@code true} when the level rose. Computed once here rather than left to each consumer
     *         to derive from the enum ordering — a consumer that compared the wrong way round would
     *         page an operator for a threat that had just receded.
     */
    public boolean isEscalation() {
        return previousLevel.map(currentLevel::isAbove).orElse(true);
    }
}
