package pk.nust.cuas.reporting.domain.port;

import java.util.Optional;
import java.util.UUID;
import pk.nust.cuas.reporting.domain.model.ReportJob;

/** Persistence for report jobs and their artifacts (ARCHITECTURE.md §4.1.10). */
public interface ReportJobRepository {

    /** Inserts a newly accepted job. */
    void save(ReportJob job);

    /** Updates a job's lifecycle state, persisting its artifact reference when it has succeeded. */
    void update(ReportJob job);

    Optional<ReportJob> findById(UUID reportId);
}
