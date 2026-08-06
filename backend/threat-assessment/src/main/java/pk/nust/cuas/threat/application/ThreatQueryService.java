package pk.nust.cuas.threat.application;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pk.nust.cuas.threat.domain.exception.AssessmentNotFoundException;
import pk.nust.cuas.threat.domain.model.ThreatAssessment;
import pk.nust.cuas.threat.domain.port.ThreatAssessmentRepository;

/** Read access to assessments for the section 4.1.7 API. */
@Service
@Transactional(readOnly = true)
public class ThreatQueryService {

    private final ThreatAssessmentRepository assessments;

    public ThreatQueryService(ThreatAssessmentRepository assessments) {
        this.assessments = assessments;
    }

    /**
     * @param trackId the track
     * @return its latest assessment
     * @throws AssessmentNotFoundException when the track has never been scored
     */
    public ThreatAssessment latest(UUID trackId) {
        return assessments.findLatest(trackId)
                .orElseThrow(() -> new AssessmentNotFoundException(trackId));
    }

    /**
     * @param trackId the track
     * @param limit   the maximum number of assessments to return
     * @return the track's assessments, newest first
     * @throws AssessmentNotFoundException when the track has never been scored
     */
    public java.util.List<ThreatAssessment> history(UUID trackId, int limit) {
        java.util.List<ThreatAssessment> history = assessments.history(trackId, limit);
        if (history.isEmpty()) {
            throw new AssessmentNotFoundException(trackId);
        }
        return history;
    }
}
