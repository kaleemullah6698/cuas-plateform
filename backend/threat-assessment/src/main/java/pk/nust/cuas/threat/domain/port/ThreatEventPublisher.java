package pk.nust.cuas.threat.domain.port;

import pk.nust.cuas.threat.domain.model.ThreatAssessment;
import pk.nust.cuas.threat.domain.model.ThreatLevelTransition;

/**
 * Publishes what this service concluded (ARCHITECTURE.md sections 4.1.7, 5.2). Declared in the
 * domain as a port; the Kafka adapter is an outward adapter.
 *
 * <p>Three topics, three different audiences, and the split matters:
 * {@code threat.assessed.v1} carries every score, for reporting and audit;
 * {@code threat.level.changed.v1} carries only transitions, because alert-service raises an alert
 * from each one; {@code threat.recommendation.generated.v1} carries the advice, which is what
 * reaches the operator and what section 2.4.3's decision chain records them accepting or overriding.
 */
public interface ThreatEventPublisher {

    /**
     * Publishes an assessment on {@code threat.assessed.v1}, keyed by track id.
     *
     * @param assessment the assessment
     */
    void publishAssessed(ThreatAssessment assessment);

    /**
     * Publishes a level transition on {@code threat.level.changed.v1}, keyed by track id.
     *
     * @param transition the transition
     */
    void publishLevelChanged(ThreatLevelTransition transition);

    /**
     * Publishes the advisory on {@code threat.recommendation.generated.v1}, keyed by track id.
     *
     * @param assessment the assessment whose recommendation to publish
     */
    void publishRecommendation(ThreatAssessment assessment);
}
