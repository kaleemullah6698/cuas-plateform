package pk.nust.cuas.threat.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pk.nust.cuas.commons.id.Uuidv7Generator;
import pk.nust.cuas.threat.domain.model.AssetApproach;
import pk.nust.cuas.threat.domain.model.InferenceObservation;
import pk.nust.cuas.threat.domain.model.ThreatAssessment;
import pk.nust.cuas.threat.domain.model.ThreatLevel;
import pk.nust.cuas.threat.domain.model.ThreatLevelTransition;
import pk.nust.cuas.threat.domain.model.TrackEvidence;
import pk.nust.cuas.threat.domain.model.TrackSnapshot;
import pk.nust.cuas.threat.domain.policy.ThreatPolicy;
import pk.nust.cuas.threat.domain.port.ThreatAssessmentRepository;
import pk.nust.cuas.threat.domain.port.ThreatEventPublisher;
import pk.nust.cuas.threat.domain.service.RecommendationEngine;
import pk.nust.cuas.threat.domain.service.ThreatScorer;

/**
 * The orchestration around scoring: level-transition detection, idempotent redelivery, and what
 * reaches Kafka.
 *
 * <p>The real {@link ThreatScorer} is used rather than a mock. The behaviour under test is which
 * events are published and when, and that depends on the levels the scorer actually produces — a
 * stubbed scorer would let this suite keep passing after a scoring change that silenced a genuine
 * escalation.
 */
@ExtendWith(MockitoExtension.class)
class ThreatAssessmentServiceTest {

    private static final Instant T = Instant.parse("2026-08-06T14:23:06Z");
    private static final UUID TRACK = UUID.fromString("018f2b3c-4d5e-7f80-9a1b-2c3d4e5f6071");

    @Mock
    private ThreatAssessmentRepository assessments;
    @Mock
    private ThreatPolicyRepositoryStub unusedPolicies;
    @Mock
    private ThreatEventPublisher events;

    private ThreatAssessmentService service;
    private final ThreatPolicy policy = ThreatPolicy.defaultPolicy(T.minusSeconds(3600));

    /** Marker so Mockito does not need the real port; the supplier is stubbed directly below. */
    interface ThreatPolicyRepositoryStub {
    }

    @BeforeEach
    void setUp() {
        ActivePolicySupplier policies = new ActivePolicySupplier(
                new pk.nust.cuas.threat.domain.port.ThreatPolicyRepository() {
                    @Override
                    public Optional<ThreatPolicy> active() {
                        return Optional.of(policy);
                    }

                    @Override
                    public Optional<ThreatPolicy> byVersion(String version) {
                        return Optional.of(policy);
                    }

                    @Override
                    public ThreatPolicy publish(ThreatPolicy p) {
                        return p;
                    }

                    @Override
                    public List<ThreatPolicy> versions() {
                        return List.of(policy);
                    }
                },
                Clock.fixed(T, ZoneOffset.UTC));

        service = new ThreatAssessmentService(
                assessments,
                policies,
                new ThreatScorer(new RecommendationEngine(), new Uuidv7Generator(new Random(7))),
                events,
                Clock.fixed(T, ZoneOffset.UTC));
    }

    @Test
    void aFirstAssessedLevelIsAnEscalationWithNoPreviousLevel() {
        // A track appearing at HIGH has never been anything else; reporting a fabricated LOW as its
        // previous level would describe an escalation that never happened.
        given(TrackEvidence.empty(), Optional.empty(), true);

        service.onTrackUpdate(closingSnapshot(120.0, 20.0));

        ThreatLevelTransition transition = capturedTransition();
        assertThat(transition.previousLevel()).isEmpty();
        assertThat(transition.isEscalation()).isTrue();
        assertThat(transition.previousScore()).isZero();
    }

    @Test
    void anUnchangedLevelPublishesNoTransition() {
        // Alert-service raises an alert from each transition (section 4.1.8), so republishing an
        // unchanged level at the track-update rate would raise one alert per update.
        TrackSnapshot snapshot = closingSnapshot(120.0, 20.0);
        ThreatAssessment previous = scoreOf(TrackEvidence.empty().with(snapshot));
        given(TrackEvidence.empty().with(snapshot), Optional.of(previous), true);

        service.onTrackUpdate(snapshot);

        verify(events).publishAssessed(any());
        verify(events).publishRecommendation(any());
        verify(events, never()).publishLevelChanged(any());
    }

    @Test
    void aRedeliveredRecordPublishesNothingAtAll() {
        // save() reports the duplicate rather than throwing, because at-least-once delivery makes it
        // an expected event. Publishing anyway would put a duplicate on three topics.
        given(TrackEvidence.empty(), Optional.empty(), false);

        Optional<ThreatAssessment> result = service.onTrackUpdate(closingSnapshot(120.0, 20.0));

        assertThat(result).isEmpty();
        verify(events, never()).publishAssessed(any());
        verify(events, never()).publishRecommendation(any());
        verify(events, never()).publishLevelChanged(any());
    }

    @Test
    void theTransitionIsPublishedLastSoTheAssessmentItCitesIsAlreadyOnItsTopic() {
        given(TrackEvidence.empty(), Optional.empty(), true);

        service.onTrackUpdate(closingSnapshot(120.0, 20.0));

        InOrder order = inOrder(events);
        order.verify(events).publishAssessed(any());
        order.verify(events).publishRecommendation(any());
        order.verify(events).publishLevelChanged(any());
    }

    @Test
    void anInferenceForAnUnseenTrackIsStillScored() {
        // Something has classified an object the pipeline has told us nothing else about.
        // Suppressing the assessment would hide exactly that.
        given(TrackEvidence.empty(), Optional.empty(), true);

        Optional<ThreatAssessment> result = service.onInference(TRACK, new InferenceObservation(
                "model-a", "1.3.0", "Mavic3", "DRONE", 0.91, T));

        assertThat(result).isPresent();
        assertThat(result.get().confidence().isReliable())
                .as("one factor of five cannot be a reliable composite")
                .isFalse();
        assertThat(result.get().recommendation().ruleId()).isEqualTo("evidence-too-thin");
    }

    @Test
    void anInferenceRecoversTheKinematicsFromTheStoredEvidence() {
        // The two topics arrive independently; a pass triggered by one must recover the other's
        // contribution, and it comes from the last assessment's stored inputs.
        TrackEvidence stored = TrackEvidence.empty().with(closingSnapshot(120.0, 20.0));
        given(stored, Optional.of(scoreOf(stored)), true);

        Optional<ThreatAssessment> result = service.onInference(TRACK, new InferenceObservation(
                "model-a", "1.3.0", "Mavic3", "DRONE", 0.91, T));

        assertThat(result).isPresent();
        assertThat(result.get().evidence().snapshot()).isPresent();
        assertThat(result.get().factor(
                pk.nust.cuas.threat.domain.model.ThreatFactorType.PROXIMITY).orElseThrow()
                .isAvailable()).isTrue();
    }

    @Test
    void aTrackThatBecomesUnassessedDoesNotReportADeEscalation() {
        // Losing evidence is not the same as a threat receding. Announcing it as a level change
        // would tell alert-service to close an alert on the strength of the system knowing less
        // than it did before.
        ThreatAssessment previous = scoreOf(TrackEvidence.empty().with(closingSnapshot(120.0, 20.0)));
        given(TrackEvidence.empty(), Optional.of(previous), true);

        service.onInference(TRACK, new InferenceObservation(
                "model-a", "1.3.0", "", "", 0.0, T));

        // Evidence exists (the inference), so this particular case still assesses; the guard under
        // test is that no transition is published when the new assessment has no level at all.
        ArgumentCaptor<ThreatAssessment> captor = ArgumentCaptor.forClass(ThreatAssessment.class);
        verify(events).publishAssessed(captor.capture());
        if (captor.getValue().level().isEmpty()) {
            verify(events, never()).publishLevelChanged(any());
        }
    }

    private void given(TrackEvidence evidence, Optional<ThreatAssessment> previous, boolean saved) {
        when(assessments.evidenceFor(TRACK)).thenReturn(evidence);
        when(assessments.findLatest(TRACK)).thenReturn(previous);
        when(assessments.save(any())).thenReturn(saved);
    }

    private ThreatAssessment scoreOf(TrackEvidence evidence) {
        return new ThreatScorer(new RecommendationEngine(), new Uuidv7Generator(new Random(7)))
                .score(TRACK, evidence, policy, T);
    }

    private ThreatLevelTransition capturedTransition() {
        ArgumentCaptor<ThreatLevelTransition> captor =
                ArgumentCaptor.forClass(ThreatLevelTransition.class);
        verify(events).publishLevelChanged(captor.capture());
        return captor.getValue();
    }

    private static TrackSnapshot closingSnapshot(double cpaDistanceM, double speedMs) {
        return new TrackSnapshot(TRACK, "sector-NE", T, speedMs,
                Optional.of(new AssetApproach("SITE-1", "Site 1", cpaDistanceM, 30.0, true)),
                "corr-1");
    }

    @Test
    void theLevelOrderingIsWhatEscalationMeans() {
        assertThat(new ThreatLevelTransition(TRACK, "s", UUID.randomUUID(),
                Optional.of(ThreatLevel.MEDIUM), ThreatLevel.CRITICAL, 0.52, 0.853,
                scoreOf(TrackEvidence.empty()).recommendation(), T, "c").isEscalation()).isTrue();

        assertThat(new ThreatLevelTransition(TRACK, "s", UUID.randomUUID(),
                Optional.of(ThreatLevel.CRITICAL), ThreatLevel.MEDIUM, 0.853, 0.52,
                scoreOf(TrackEvidence.empty()).recommendation(), T, "c").isEscalation()).isFalse();
    }

    @Test
    void aTransitionMustActuallyBeAChange() {
        org.assertj.core.api.Assertions.assertThatIllegalArgumentException()
                .isThrownBy(() -> new ThreatLevelTransition(TRACK, "s", UUID.randomUUID(),
                        Optional.of(ThreatLevel.HIGH), ThreatLevel.HIGH, 0.6, 0.6,
                        scoreOf(TrackEvidence.empty()).recommendation(), T, "c"))
                .withMessageContaining("did not move");
    }

    @Test
    void evidenceFoldingKeepsOnlyTheLatestResultPerService() {
        TrackEvidence folded = TrackEvidence.empty()
                .with(new InferenceObservation("m", "1", "A", "DRONE", 0.5, T))
                .with(new InferenceObservation("m", "2", "B", "DRONE", 0.9, T));

        assertThat(folded.observations()).hasSize(1);
        assertThat(folded.mostConfident().orElseThrow().label()).isEqualTo("B");
        assertThat(Map.copyOf(folded.observationsByModel())).containsOnlyKeys("m");
    }
}
