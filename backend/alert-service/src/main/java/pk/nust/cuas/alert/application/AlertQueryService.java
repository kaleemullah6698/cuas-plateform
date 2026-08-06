package pk.nust.cuas.alert.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pk.nust.cuas.alert.domain.exception.AlertNotFoundException;
import pk.nust.cuas.alert.domain.model.Acknowledgement;
import pk.nust.cuas.alert.domain.model.Alert;
import pk.nust.cuas.alert.domain.model.AlertStatus;
import pk.nust.cuas.alert.domain.model.Escalation;
import pk.nust.cuas.alert.domain.model.Severity;
import pk.nust.cuas.alert.domain.port.AlertRepository;

/** Read access to alerts for the section 4.1.8 API. */
@Service
@Transactional(readOnly = true)
public class AlertQueryService {

    private final AlertRepository alerts;

    public AlertQueryService(AlertRepository alerts) {
        this.alerts = alerts;
    }

    /**
     * @param status   filter by status when given
     * @param severity filter by severity when given
     * @param sectorId filter by sector when given
     * @param limit    the maximum number to return
     * @return matching alerts, newest raised first
     */
    public List<Alert> list(Optional<AlertStatus> status, Optional<Severity> severity,
                            Optional<String> sectorId, int limit) {
        return alerts.search(status, severity, sectorId, limit);
    }

    /**
     * @param alertId the alert
     * @return it
     * @throws AlertNotFoundException when no such alert exists
     */
    public Alert get(UUID alertId) {
        return alerts.findById(alertId).orElseThrow(() -> new AlertNotFoundException(alertId));
    }

    /**
     * @param alertId the alert
     * @return its acknowledgements, oldest first
     */
    public List<Acknowledgement> acknowledgements(UUID alertId) {
        get(alertId);
        return alerts.acknowledgements(alertId);
    }

    /**
     * @param alertId the alert
     * @return its escalations, oldest first
     */
    public List<Escalation> escalations(UUID alertId) {
        get(alertId);
        return alerts.escalations(alertId);
    }
}
