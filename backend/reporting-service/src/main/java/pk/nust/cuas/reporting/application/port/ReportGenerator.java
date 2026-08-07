package pk.nust.cuas.reporting.application.port;

import pk.nust.cuas.reporting.domain.model.ReportDocument;
import pk.nust.cuas.reporting.domain.model.ReportJob;
import pk.nust.cuas.reporting.domain.model.ReportType;

/**
 * Builds the structured content of one report type from the projection read model (ARCHITECTURE.md
 * §4.1.10). One generator per {@link ReportType}; each produces a {@link ReportDocument} that any
 * renderer can turn into a format.
 */
public interface ReportGenerator {

    ReportType type();

    /**
     * @param job the accepted job, carrying the report parameters
     * @return the report content
     * @throws pk.nust.cuas.reporting.application.ReportDataException if the parameters are unusable or
     *                                                                the query matches nothing
     */
    ReportDocument generate(ReportJob job);
}
