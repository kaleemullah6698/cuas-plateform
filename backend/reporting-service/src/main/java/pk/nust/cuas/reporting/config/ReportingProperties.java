package pk.nust.cuas.reporting.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Tunable parameters of report generation (ARCHITECTURE.md §4.1.10), bound from {@code reporting.*}
 * with defaults that make the service runnable with no configuration.
 *
 * @param artifactRoot the directory rendered report artifacts are stored under
 * @param worker       the async generation pool
 */
@ConfigurationProperties(prefix = "reporting")
public record ReportingProperties(@DefaultValue("./reports") String artifactRoot,
                                  @DefaultValue Worker worker) {

    /**
     * @param coreThreads  threads kept alive for generation
     * @param maxThreads   the ceiling under load
     * @param queueCapacity how many accepted jobs may wait for a thread
     */
    public record Worker(@DefaultValue("2") int coreThreads,
                         @DefaultValue("4") int maxThreads,
                         @DefaultValue("50") int queueCapacity) {
    }
}
