package pk.nust.cuas.alert.infrastructure.persistence;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pk.nust.cuas.alert.infrastructure.persistence.entity.AlertEntity;
import pk.nust.cuas.alert.infrastructure.persistence.repository.AlertJpaRepositories;

/**
 * Inserts a new alert row in its own transaction, so the {@code uq_alert_open_dedup} race can be lost
 * gracefully.
 *
 * <p>A unique-constraint violation marks its transaction rollback-only; catching the exception inside
 * that same transaction does not undo the mark, so the transaction still fails to commit. This runs
 * the insert under {@code REQUIRES_NEW} and — crucially — does <em>not</em> catch: the exception must
 * leave this boundary so this inner transaction <em>rolls back</em> cleanly rather than committing.
 * The caller then catches it outside, with no surrounding transaction poisoned. Separated into its
 * own bean because Spring's transaction proxy does not apply to a self-invocation.
 */
@Component
class AlertInsertOperation {

    private final AlertJpaRepositories.AlertJpa alerts;

    AlertInsertOperation(AlertJpaRepositories.AlertJpa alerts) {
        this.alerts = alerts;
    }

    /**
     * Flushes the insert immediately so the constraint fires here, not at the outer commit — a
     * violation surfacing later would already have published {@code alert.raised.v1}, putting the
     * duplicate on the operator's screen before failing.
     *
     * @param entity the alert row to insert
     * @throws org.springframework.dao.DataIntegrityViolationException if an open alert already exists
     *         for the same deduplication key
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void insert(AlertEntity entity) {
        alerts.saveAndFlush(entity);
    }
}
