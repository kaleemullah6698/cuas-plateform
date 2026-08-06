package pk.nust.cuas.threat.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import pk.nust.cuas.threat.domain.model.AdvisoryRecommendation;
import pk.nust.cuas.threat.domain.model.AssessmentConfidence;
import pk.nust.cuas.threat.domain.model.Recommendation;
import pk.nust.cuas.threat.domain.model.ThreatAssessment;
import pk.nust.cuas.threat.domain.model.ThreatLevel;
import pk.nust.cuas.threat.domain.model.TrackEvidence;
import pk.nust.cuas.threat.domain.port.ThreatAssessmentRepository;
import pk.nust.cuas.threat.infrastructure.persistence.entity.AssessmentEntity;
import pk.nust.cuas.threat.infrastructure.persistence.entity.ThreatScoreEntity;
import pk.nust.cuas.threat.infrastructure.persistence.mapper.AssessmentJsonCodec;
import pk.nust.cuas.threat.infrastructure.persistence.repository.AssessmentJpaRepository;
import pk.nust.cuas.threat.infrastructure.persistence.repository.ThreatScoreJpaRepository;

/**
 * JPA adapter for {@link ThreatAssessmentRepository} — the outward persistence adapter for the
 * immutable assessment history and the current-score projection.
 */
@Repository
public class JpaThreatAssessmentRepository implements ThreatAssessmentRepository {

    private final AssessmentJpaRepository assessments;
    private final ThreatScoreJpaRepository scores;
    private final AssessmentJsonCodec codec;

    public JpaThreatAssessmentRepository(AssessmentJpaRepository assessments,
                                         ThreatScoreJpaRepository scores,
                                         AssessmentJsonCodec codec) {
        this.assessments = assessments;
        this.scores = scores;
        this.codec = codec;
    }

    /**
     * {@inheritDoc}
     *
     * <p>The duplicate check is made twice: a read first, because that is the common path and costs
     * one index lookup, and then the database's own unique constraint, because two instances can
     * pass the read at the same moment. Relying on the read alone would leave a window exactly wide
     * enough for two consumers rebalancing onto the same partition to both insert — and a duplicate
     * assessment becomes a duplicate level transition, which becomes the duplicate alert section 5.7
     * names as the failure that destroys operator trust fastest.
     */
    @Override
    @Transactional
    public boolean save(ThreatAssessment assessment) {
        if (assessments.findByTrackIdAndInputsHash(
                assessment.trackId(), assessment.inputsHash()).isPresent()) {
            return false;
        }
        try {
            assessments.saveAndFlush(toEntity(assessment));
        } catch (DataIntegrityViolationException ex) {
            // The unique constraint fired: another instance stored the identical assessment first.
            return false;
        }
        advanceScore(assessment);
        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ThreatAssessment> findLatest(UUID trackId) {
        return assessments
                .findByTrackIdOrderByAssessedAtDesc(trackId, PageRequest.of(0, 1))
                .stream()
                .findFirst()
                .map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public TrackEvidence evidenceFor(UUID trackId) {
        return findLatest(trackId).map(ThreatAssessment::evidence).orElseGet(TrackEvidence::empty);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ThreatAssessment> history(UUID trackId, int limit) {
        return assessments
                .findByTrackIdOrderByAssessedAtDesc(trackId, PageRequest.of(0, limit))
                .stream()
                .map(this::toDomain)
                .toList();
    }

    /**
     * Advances the current-score projection, guarding against an out-of-order write.
     *
     * <p>Two events for one track can be processed concurrently by different instances, and the one
     * that commits second is not necessarily the one describing the later observation. The
     * projection therefore only moves forward: an assessment of an older track estimate never
     * overwrites a newer one. The immutable history keeps both regardless.
     */
    private void advanceScore(ThreatAssessment assessment) {
        Instant now = assessment.assessedAt();
        String level = assessment.level().map(Enum::name).orElse(null);
        scores.findById(assessment.trackId()).ifPresentOrElse(existing -> {
            if (!existing.getAssessedAt().isAfter(assessment.assessedAt())) {
                existing.advanceTo(assessment.assessmentId(), assessment.sectorId(), level,
                        assessment.compositeScore(),
                        assessment.recommendation().recommendation().name(),
                        assessment.assessedAt(), assessment.observedAt(), now);
                scores.save(existing);
            }
        }, () -> scores.save(new ThreatScoreEntity(
                assessment.trackId(), assessment.assessmentId(), assessment.sectorId(), level,
                assessment.compositeScore(), assessment.recommendation().recommendation().name(),
                assessment.assessedAt(), assessment.observedAt(), now)));
    }

    private AssessmentEntity toEntity(ThreatAssessment assessment) {
        return new AssessmentEntity(
                assessment.assessmentId(),
                assessment.trackId(),
                assessment.sectorId(),
                assessment.compositeScore(),
                assessment.level().map(Enum::name).orElse(null),
                assessment.recommendation().recommendation().name(),
                assessment.recommendation().ruleId(),
                assessment.recommendation().rationale(),
                assessment.policyId(),
                assessment.policyVersion(),
                assessment.policyEffectiveAt(),
                assessment.confidence().availableWeight(),
                assessment.confidence().factorsAvailable(),
                assessment.confidence().factorsTotal(),
                assessment.confidence().isReliable(),
                codec.writeFactors(assessment.factors()),
                codec.writeEvidence(assessment.evidence()),
                assessment.inputsHash(),
                assessment.assessedAt(),
                assessment.observedAt(),
                assessment.correlationId());
    }

    private ThreatAssessment toDomain(AssessmentEntity entity) {
        return new ThreatAssessment(
                entity.getAssessmentId(),
                entity.getTrackId(),
                entity.getSectorId(),
                entity.getCompositeScore(),
                Optional.ofNullable(entity.getLevel()).map(ThreatLevel::valueOf),
                codec.readFactors(entity.getFactorsJson()),
                new AssessmentConfidence(entity.getAvailableWeight(), entity.getFactorsAvailable(),
                        entity.getFactorsTotal(), entity.isReliable()),
                new AdvisoryRecommendation(
                        Recommendation.valueOf(entity.getRecommendation()),
                        entity.getRuleId(), entity.getRationale()),
                entity.getPolicyId(),
                entity.getPolicyVersion(),
                entity.getPolicyEffectiveAt(),
                codec.readEvidence(entity.getEvidenceJson()),
                entity.getInputsHash(),
                entity.getAssessedAt(),
                entity.getObservedAt(),
                entity.getCorrelationId());
    }
}
