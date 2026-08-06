package pk.nust.cuas.alert.domain.model;

/** The incident lifecycle, matching the states section 2.4.3 renders in its header. */
public enum IncidentStatus {

    OPEN,
    UNDER_REVIEW,
    CLOSED;

    /** @return {@code true} when the investigation is finished */
    public boolean isClosed() {
        return this == CLOSED;
    }
}
