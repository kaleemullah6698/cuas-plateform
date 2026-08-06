package pk.nust.cuas.alert.domain.port;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import pk.nust.cuas.alert.domain.model.EvidenceRef;
import pk.nust.cuas.alert.domain.model.Incident;
import pk.nust.cuas.alert.domain.model.IncidentEvent;
import pk.nust.cuas.alert.domain.model.IncidentNote;
import pk.nust.cuas.alert.domain.model.IncidentStatus;

/**
 * Persistence for incidents, their timeline, notes and evidence (ARCHITECTURE.md section 4.1.8).
 *
 * <p>The timeline, the notes and the evidence are all append-only. They are the investigative record
 * section 2.4.3 exports, and a record that could be rewritten after the decisions it justified were
 * taken is not one a reviewer can rely on.
 */
public interface IncidentRepository {

    /** Persists a newly declared incident. */
    void create(Incident incident);

    /** Persists a mutated incident: assignment, severity growth, closure. */
    void save(Incident incident);

    /**
     * @param incidentId the investigation
     * @return it, or empty when no such incident exists
     */
    Optional<Incident> findById(UUID incidentId);

    /**
     * Lists incidents for the read API, newest opened first.
     *
     * @param status filter by status when given
     * @param limit  the maximum number to return
     * @return the matching incidents
     */
    List<Incident> search(Optional<IncidentStatus> status, int limit);

    /** Appends one line to the timeline. */
    void appendEvent(IncidentEvent event);

    /**
     * @param incidentId the investigation
     * @return its timeline, oldest first
     */
    List<IncidentEvent> timeline(UUID incidentId);

    /** Appends a note. */
    void appendNote(IncidentNote note);

    /**
     * @param incidentId the investigation
     * @return its notes, oldest first
     */
    List<IncidentNote> notes(UUID incidentId);

    /** Attaches an evidence reference. */
    void attachEvidence(EvidenceRef evidence);

    /**
     * @param incidentId the investigation
     * @return its evidence bundle
     */
    List<EvidenceRef> evidence(UUID incidentId);

    /**
     * @param year the calendar year
     * @return how many incidents have been declared in it, for reference numbering
     */
    long countDeclaredIn(int year);
}
