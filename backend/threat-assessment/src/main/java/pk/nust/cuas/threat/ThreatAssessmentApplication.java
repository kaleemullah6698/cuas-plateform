package pk.nust.cuas.threat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Composite threat scoring, rules engine, advisory recommendations (ARCHITECTURE.md section 4.1.7).
 *
 * <p>Consumes {@code track.updated.v1} and {@code ai.inference.completed.v1}, scores each track
 * against the policy in force, and publishes {@code threat.assessed.v1},
 * {@code threat.level.changed.v1} and {@code threat.recommendation.generated.v1}. Owns
 * {@code threatdb}.
 *
 * <p><strong>The weighted rules engine is here, in Track A.</strong> Section 6.3.4 describes
 * {@code threat-scorer} as a rules engine plus a gradient-boosted residual; section 4.1.7 settles
 * that only the residual is a Track B service, and it reaches this service as an ordinary inference
 * event weighed as one factor of five. There is no AI client in this module. Consequently this
 * service scores, levels and advises with <em>zero models present</em> — the same property section
 * 6.4 gives the rest of Track A.
 *
 * <p><strong>Every recommendation is advisory.</strong> The output vocabulary is section 6.3.4's
 * closed five; no code path here can emit an engagement action, and none can be added without an
 * architecture amendment.
 */
@SpringBootApplication
public class ThreatAssessmentApplication {

    public static void main(String[] args) {
        SpringApplication.run(ThreatAssessmentApplication.class, args);
    }
}
