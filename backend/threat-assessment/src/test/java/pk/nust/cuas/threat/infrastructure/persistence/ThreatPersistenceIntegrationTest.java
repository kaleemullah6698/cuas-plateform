package pk.nust.cuas.threat.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import pk.nust.cuas.commons.id.Uuidv7Generator;
import pk.nust.cuas.threat.domain.model.AssetApproach;
import pk.nust.cuas.threat.domain.model.InferenceObservation;
import pk.nust.cuas.threat.domain.model.ThreatAssessment;
import pk.nust.cuas.threat.domain.model.ThreatFactorType;
import pk.nust.cuas.threat.domain.model.TrackEvidence;
import pk.nust.cuas.threat.domain.model.TrackSnapshot;
import pk.nust.cuas.threat.domain.policy.LevelThresholds;
import pk.nust.cuas.threat.domain.policy.NormalisationParameters;
import pk.nust.cuas.threat.domain.policy.ThreatPolicy;
import pk.nust.cuas.threat.domain.port.ThreatAssessmentRepository;
import pk.nust.cuas.threat.domain.port.ThreatPolicyRepository;
import pk.nust.cuas.threat.domain.service.RecommendationEngine;
import pk.nust.cuas.threat.domain.service.ThreatScorer;

/**
 * The persistence adapters against a real PostgreSQL (ARCHITECTURE.md section 4.1.7).
 *
 * <p>Against a real database rather than H2, because the three things that matter here are exactly
 * the ones a substitute gets approximately right: that the Flyway migration applies and Hibernate
 * validates against it, that a {@code jsonb} column round-trips the evidence a score was computed
 * from, and that the unique constraint on {@code (track_id, inputs_hash)} actually rejects a
 * concurrent duplicate. An unverified migration is the kind of technical debt that only surfaces on
 * a deployment.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class ThreatPersistenceIntegrationTest {

    private static final Instant T = Instant.parse("2026-08-06T14:23:06Z");

    @Container
    @SuppressWarnings("resource")
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("threatdb");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        // No broker in this suite; the listeners would otherwise retry a connection for its duration.
        registry.add("spring.kafka.listener.auto-startup", () -> "false");
    }

    @Autowired
    private ThreatAssessmentRepository assessments;

    @Autowired
    private ThreatPolicyRepository policies;

    private final ThreatScorer scorer =
            new ThreatScorer(new RecommendationEngine(), new Uuidv7Generator(new Random(3)));

    // ── Schema ──────────────────────────────────────────────────────────────────────────────────

    @Test
    void theMigrationAppliesAndHibernateValidatesAgainstIt() {
        // ddl-auto: validate means the context would not have started if any mapping disagreed with
        // the migration. Reaching this assertion is the assertion.
        assertThat(assessments).isNotNull();
        assertThat(policies).isNotNull();
    }

    // ── Assessments ─────────────────────────────────────────────────────────────────────────────

    @Test
    void anAssessmentRoundTripsIncludingItsEvidenceAndBreakdown() {
        UUID track = UUID.randomUUID();
        ThreatAssessment original = scorer.score(track, richEvidence(track), activePolicy(), T);

        assertThat(assessments.save(original)).isTrue();
        ThreatAssessment reloaded = assessments.findLatest(track).orElseThrow();

        assertThat(reloaded.assessmentId()).isEqualTo(original.assessmentId());
        assertThat(reloaded.compositeScore()).isEqualTo(original.compositeScore());
        assertThat(reloaded.level()).isEqualTo(original.level());
        assertThat(reloaded.policyVersion()).isEqualTo(original.policyVersion());
        assertThat(reloaded.inputsHash()).isEqualTo(original.inputsHash());
        assertThat(reloaded.recommendation()).isEqualTo(original.recommendation());

        // The breakdown survives the jsonb column, absent factors and all.
        assertThat(reloaded.factors()).hasSameSizeAs(original.factors());
        assertThat(reloaded.factor(ThreatFactorType.PROXIMITY).orElseThrow())
                .isEqualTo(original.factor(ThreatFactorType.PROXIMITY).orElseThrow());

        // And so does the evidence, which is what makes the score reproducible from the record.
        assertThat(reloaded.evidence().snapshot()).isPresent();
        assertThat(reloaded.evidence().observations()).hasSize(2);
        assertThat(reloaded.evidence().consecutiveClosingCount())
                .isEqualTo(original.evidence().consecutiveClosingCount());
    }

    @Test
    void aStoredAssessmentReproducesItsOwnScore() {
        // The whole reason the inputs are persisted: replaying them against the recorded policy
        // version must give the recorded number back.
        UUID track = UUID.randomUUID();
        ThreatPolicy policy = activePolicy();
        ThreatAssessment original = scorer.score(track, richEvidence(track), policy, T);
        assessments.save(original);

        ThreatAssessment reloaded = assessments.findLatest(track).orElseThrow();
        ThreatAssessment recomputed = scorer.score(
                track, reloaded.evidence(),
                policies.byVersion(reloaded.policyVersion()).orElseThrow(), T);

        assertThat(recomputed.compositeScore()).isEqualTo(original.compositeScore());
        assertThat(recomputed.level()).isEqualTo(original.level());
    }

    @Test
    void anIdenticalRedeliveryIsRejectedByTheUniqueConstraint() {
        UUID track = UUID.randomUUID();
        ThreatAssessment first = scorer.score(track, richEvidence(track), activePolicy(), T);
        ThreatAssessment redelivered = scorer.score(track, richEvidence(track), activePolicy(), T);

        assertThat(assessments.save(first)).isTrue();
        assertThat(redelivered.inputsHash()).isEqualTo(first.inputsHash());
        assertThat(assessments.save(redelivered))
                .as("a duplicate is reported, not thrown — at-least-once makes it an expected event")
                .isFalse();
        assertThat(assessments.history(track, 10)).hasSize(1);
    }

    @Test
    void newEvidenceForTheSameTrackIsANewAssessment() {
        UUID track = UUID.randomUUID();
        TrackEvidence base = TrackEvidence.empty().with(snapshot(track, 640.0));
        assessments.save(scorer.score(track, base, activePolicy(), T));

        TrackEvidence withVerdict = base.with(observation("model-a", "Mavic3", 0.91));
        assertThat(assessments.save(scorer.score(track, withVerdict, activePolicy(), T))).isTrue();

        assertThat(assessments.history(track, 10)).hasSize(2);
    }

    @Test
    void anUnassessedTrackStoresANullLevelNotALowOne() {
        UUID track = UUID.randomUUID();
        assessments.save(scorer.score(track, TrackEvidence.empty(), activePolicy(), T));

        assertThat(assessments.findLatest(track).orElseThrow().level()).isEmpty();
    }

    @Test
    void evidenceForAnUnknownTrackIsEmptyRatherThanAbsent() {
        assertThat(assessments.evidenceFor(UUID.randomUUID()).snapshot()).isEmpty();
    }

    @Test
    void historyIsNewestFirst() {
        UUID track = UUID.randomUUID();
        TrackEvidence base = TrackEvidence.empty().with(snapshot(track, 640.0));
        assessments.save(scorer.score(track, base, activePolicy(), T));
        assessments.save(scorer.score(track, base.with(observation("m", "A", 0.5)),
                activePolicy(), T.plusSeconds(5)));

        List<ThreatAssessment> history = assessments.history(track, 10);

        assertThat(history).hasSize(2);
        assertThat(history.get(0).assessedAt()).isAfter(history.get(1).assessedAt());
    }

    @Test
    void anOlderObservationNeverOverwritesANewerCurrentScore() {
        // Two instances can process one track concurrently, and the one that commits second is not
        // necessarily the one describing the later observation. The projection only moves forward;
        // the immutable history keeps both.
        UUID track = UUID.randomUUID();
        TrackEvidence base = TrackEvidence.empty().with(snapshot(track, 640.0));
        ThreatAssessment newer = scorer.score(track, base, activePolicy(), T.plusSeconds(30));
        ThreatAssessment older = scorer.score(
                track, base.with(observation("m", "A", 0.5)), activePolicy(), T);

        assessments.save(newer);
        assessments.save(older);

        assertThat(assessments.history(track, 10)).hasSize(2);
        // The read API answers from the projection, which still reflects the newer assessment.
        assertThat(assessments.findLatest(track).orElseThrow().assessedAt())
                .isEqualTo(newer.assessedAt());
    }

    // ── Policies ────────────────────────────────────────────────────────────────────────────────

    @Test
    void publishingAVersionMakesItActiveAndLeavesTheOldOneUntouched() {
        ThreatPolicy first = activePolicy();
        ThreatPolicy second = new ThreatPolicy(
                first.policyId(), "policy-under-test-" + UUID.randomUUID(), T,
                ThreatPolicy.DEFAULT_WEIGHTS, new LevelThresholds(0.25, 0.50, 0.75),
                NormalisationParameters.DEFAULT, 0.6, ThreatPolicy.defaultRules());

        policies.publish(second);

        assertThat(policies.active().orElseThrow().version()).isEqualTo(second.version());
        // The superseded version is still readable exactly as written — which is what makes every
        // assessment that cites it still reproducible.
        ThreatPolicy reloadedFirst = policies.byVersion(first.version()).orElseThrow();
        assertThat(reloadedFirst.thresholds()).isEqualTo(first.thresholds());
        assertThat(reloadedFirst.reliabilityFloor()).isEqualTo(first.reliabilityFloor());
    }

    @Test
    void aPolicyRoundTripsWithItsRules() {
        ThreatPolicy stored = policies.byVersion(activePolicy().version()).orElseThrow();

        assertThat(stored.rules())
                .extracting(r -> r.ruleId())
                .containsExactlyElementsOf(
                        ThreatPolicy.defaultRules().stream().map(r -> r.ruleId()).toList());
        assertThat(stored.weights()).isEqualTo(ThreatPolicy.DEFAULT_WEIGHTS);
    }

    // ── Fixtures ────────────────────────────────────────────────────────────────────────────────

    private ThreatPolicy activePolicy() {
        return policies.active().orElseGet(() -> policies.publish(ThreatPolicy.defaultPolicy(T)));
    }

    private TrackEvidence richEvidence(UUID track) {
        return TrackEvidence.empty()
                .with(snapshot(track, 640.0))
                .with(snapshot(track, 620.0))
                .with(observation("model-a", "Mavic3", 0.91))
                .with(observation("model-b", "Mavic3", 0.84));
    }

    private static TrackSnapshot snapshot(UUID track, double cpaDistanceM) {
        return new TrackSnapshot(track, "sector-NE", T, 14.0,
                Optional.of(new AssetApproach("SITE-1", "Site 1", cpaDistanceM, 47.0, true)),
                "corr-1");
    }

    private static InferenceObservation observation(String modelId, String label, double confidence) {
        return new InferenceObservation(modelId, "1.3.0", label, "DRONE", confidence, T);
    }
}
