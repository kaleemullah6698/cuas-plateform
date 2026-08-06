package pk.nust.cuas.threat.domain.model;

import java.util.List;

/**
 * How much of the scoring model actually had evidence behind it.
 *
 * <p>Reported alongside the score, never folded into it. A 0.62 computed from all five factors and a
 * 0.62 computed from two are different claims about the same airspace, and an operator who cannot
 * tell them apart cannot calibrate their trust in the number. Section 6.3.4 requires the assessment
 * to say when a factor is missing; this is the aggregate form of that statement.
 *
 * @param availableWeight  sum of the weights of the factors that had evidence, in {@code [0,1]};
 *                         1.0 means the full model was evaluated
 * @param factorsAvailable how many factors had evidence
 * @param factorsTotal     how many the policy defines
 * @param isReliable       whether {@code availableWeight} met the policy's floor
 */
public record AssessmentConfidence(
        double availableWeight,
        int factorsAvailable,
        int factorsTotal,
        boolean isReliable) {

    public AssessmentConfidence {
        if (availableWeight < 0.0 || availableWeight > 1.0 + 1e-9) {
            throw new IllegalArgumentException(
                    "Available weight must be in [0,1], was " + availableWeight);
        }
        if (factorsAvailable < 0 || factorsAvailable > factorsTotal) {
            throw new IllegalArgumentException(
                    "Available factor count must be between 0 and the total, was " + factorsAvailable);
        }
    }

    /**
     * Summarises a scored factor set.
     *
     * @param factors         every factor the policy defined, available or not
     * @param reliabilityFloor the policy's minimum available weight for the score to be reliable
     * @return the confidence summary
     */
    public static AssessmentConfidence of(List<ThreatFactor> factors, double reliabilityFloor) {
        double available = factors.stream()
                .filter(ThreatFactor::isAvailable)
                .mapToDouble(ThreatFactor::weight)
                .sum();
        int count = (int) factors.stream().filter(ThreatFactor::isAvailable).count();
        return new AssessmentConfidence(
                Math.min(1.0, available), count, factors.size(), available >= reliabilityFloor);
    }

    /** @return {@code true} when no factor at all could be evaluated */
    public boolean isEmpty() {
        return factorsAvailable == 0;
    }
}
