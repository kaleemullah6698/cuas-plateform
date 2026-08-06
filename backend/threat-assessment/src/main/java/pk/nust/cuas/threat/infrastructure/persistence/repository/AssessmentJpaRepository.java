package pk.nust.cuas.threat.infrastructure.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import pk.nust.cuas.threat.infrastructure.persistence.entity.AssessmentEntity;

/** Spring Data repository for {@code assessments}. */
public interface AssessmentJpaRepository extends JpaRepository<AssessmentEntity, UUID> {

    List<AssessmentEntity> findByTrackIdOrderByAssessedAtDesc(UUID trackId, Pageable pageable);

    /** The idempotency lookup: has this exact input set already been scored for this track? */
    Optional<AssessmentEntity> findByTrackIdAndInputsHash(UUID trackId, String inputsHash);
}
