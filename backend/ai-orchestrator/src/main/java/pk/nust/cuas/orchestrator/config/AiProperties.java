package pk.nust.cuas.orchestrator.config;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * The AI service registry, as configuration (ARCHITECTURE.md section 6.4).
 *
 * <p>This record is the whole of section 6.1's "adding a fifth model is a configuration entry".
 * Services are a <b>map keyed by service id</b>, not a fixed set of fields, so a new model — radar
 * fusion, acoustic, GNSS, anything — is a new YAML block and nothing else. Nothing in this file, or
 * anywhere else in the orchestrator, names a model.
 *
 * <pre>
 * ai:
 *   services:
 *     some-service-id:
 *       modalities: [RF]
 *       mode: mock          # or grpc, with an endpoint
 *       timeout: PT0.1S
 *       max-concurrent: 32
 * </pre>
 *
 * @param services the registered services, keyed by id; empty is valid and means nothing is
 *                 registered, which a deployment may legitimately want
 */
@ConfigurationProperties(prefix = "ai")
public record AiProperties(@DefaultValue Map<String, Service> services) {

    public AiProperties {
        // LinkedHashMap preserves the declaration order the registry relies on for deterministic
        // routing; Spring's binder supplies an ordered map, and this keeps it ordered on copy.
        services = services == null ? Map.of() : new LinkedHashMap<>(services);
    }

    /**
     * One registered service.
     *
     * @param modalities    the detection modalities it accepts, e.g. {@code [RF]} or {@code [RF, RADAR]}
     * @param mode          {@code mock} or {@code grpc}
     * @param endpoint      {@code host:port}; required when mode is grpc
     * @param timeout       the orchestrator-enforced budget for one call
     * @param modelVersion  a version to pin, or blank for the service default (sections 6.6 canary
     *                      and rollback)
     * @param maxConcurrent bulkhead permits, bounding concurrency so a burst never overwhelms a GPU
     */
    public record Service(
            List<String> modalities,
            @DefaultValue("mock") String mode,
            @DefaultValue("") String endpoint,
            @DefaultValue("PT1S") Duration timeout,
            @DefaultValue("") String modelVersion,
            @DefaultValue("32") int maxConcurrent) {
    }
}
