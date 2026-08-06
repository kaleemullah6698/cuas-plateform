package pk.nust.cuas.alert.domain.port;

import pk.nust.cuas.alert.domain.model.Acknowledgement;
import pk.nust.cuas.alert.domain.model.Alert;
import pk.nust.cuas.alert.domain.model.Escalation;
import pk.nust.cuas.alert.domain.model.Incident;
import pk.nust.cuas.alert.domain.model.Resolution;

/**
 * Publishes what this service decided (ARCHITECTURE.md sections 4.1.8, 5.2). Declared in the domain
 * as a port; the Kafka adapter is an outward adapter.
 *
 * <p>Seven topics. The alert ones are keyed by alert id and the incident ones by incident id, so
 * each aggregate's events stay ordered on one partition — a consumer must never see an
 * acknowledgement before the raise it answers.
 */
public interface AlertEventPublisher {

    /** {@code alert.raised.v1}. */
    void publishRaised(Alert alert);

    /** {@code alert.acknowledged.v1}. */
    void publishAcknowledged(Alert alert, Acknowledgement acknowledgement);

    /** {@code alert.escalated.v1}. */
    void publishEscalated(Alert alert, Escalation escalation);

    /** {@code alert.resolved.v1}. */
    void publishResolved(Alert alert, Resolution resolution);

    /** {@code incident.created.v1}. */
    void publishIncidentCreated(Incident incident);

    /** {@code incident.updated.v1}. */
    void publishIncidentUpdated(Incident incident);

    /** {@code incident.closed.v1}. */
    void publishIncidentClosed(Incident incident);
}
