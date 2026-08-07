package pk.nust.cuas.reporting.application;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import pk.nust.cuas.reporting.application.port.ReportGenerator;
import pk.nust.cuas.reporting.domain.model.ReportType;

/**
 * Resolves the {@link ReportGenerator} for a report type. Built from every generator bean, indexed by
 * type; rejects a duplicate at startup and fails a lookup with no generator, so a missing report type
 * is a loud wiring error rather than a silently dropped job.
 */
@Component
public class ReportGeneratorRegistry {

    private final Map<ReportType, ReportGenerator> generators = new EnumMap<>(ReportType.class);

    public ReportGeneratorRegistry(List<ReportGenerator> generators) {
        for (ReportGenerator generator : generators) {
            ReportGenerator existing = this.generators.putIfAbsent(generator.type(), generator);
            if (existing != null) {
                throw new IllegalStateException("two generators for report type " + generator.type());
            }
        }
    }

    public ReportGenerator forType(ReportType type) {
        ReportGenerator generator = generators.get(type);
        if (generator == null) {
            throw new IllegalStateException("no generator for report type " + type);
        }
        return generator;
    }
}
