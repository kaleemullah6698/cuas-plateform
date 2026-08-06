package pk.nust.cuas.alert.config;

import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import pk.nust.cuas.alert.domain.model.Severity;
import pk.nust.cuas.alert.domain.policy.EscalationPolicy;
import pk.nust.cuas.alert.domain.policy.SuppressionPolicy;
import pk.nust.cuas.alert.domain.port.EscalationScheduler;
import pk.nust.cuas.alert.domain.port.EvidenceStore;
import pk.nust.cuas.alert.domain.port.LeaderElection;
import pk.nust.cuas.alert.domain.service.AlertRaisingPolicy;
import pk.nust.cuas.alert.infrastructure.cache.RedisEscalationScheduler;
import pk.nust.cuas.alert.infrastructure.cache.RedisLeaderElection;
import pk.nust.cuas.alert.infrastructure.evidence.FilesystemEvidenceStore;
import pk.nust.cuas.commons.id.Uuidv7Generator;

/**
 * Wires the framework-free domain policies and the infrastructure adapters.
 *
 * <p>The policies carry no Spring annotations: they compile without Spring on the classpath, which
 * is the layering test CLAUDE.md section 2 sets for {@code domain/}. They become beans here, where
 * knowing about Spring is the package's job.
 *
 * <p>A configured severity, suppression window or escalation rung that is missing falls back to the
 * documented default rather than to nothing. A half-configured policy is the likeliest
 * misconfiguration, and the failure mode of "no ladder" is an alert that never escalates — silent,
 * and only discovered when somebody asks why nobody was paged.
 */
@Configuration
@EnableConfigurationProperties(AlertProperties.class)
public class AlertServiceConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public Uuidv7Generator uuidv7Generator() {
        return new Uuidv7Generator(new SecureRandom());
    }

    @Bean
    public AlertRaisingPolicy alertRaisingPolicy(AlertProperties properties) {
        return new AlertRaisingPolicy(Severity.valueOf(properties.minimumSeverity()));
    }

    @Bean
    public SuppressionPolicy suppressionPolicy(AlertProperties properties) {
        Map<Severity, Duration> windows = new EnumMap<>(Severity.class);
        for (Severity severity : Severity.values()) {
            windows.put(severity, properties.suppression().getOrDefault(
                    severity.name(), SuppressionPolicy.DEFAULT.windowFor(severity)));
        }
        return new SuppressionPolicy(windows);
    }

    @Bean
    public EscalationPolicy escalationPolicy(AlertProperties properties) {
        Map<Severity, List<Duration>> ladders = new EnumMap<>(Severity.class);
        for (Severity severity : Severity.values()) {
            List<Duration> configured = properties.escalation().get(severity.name());
            ladders.put(severity, configured == null || configured.isEmpty()
                    ? EscalationPolicy.DEFAULT.ladders().get(severity)
                    : configured);
        }
        return new EscalationPolicy(ladders);
    }

    @Bean
    public EscalationScheduler escalationScheduler(StringRedisTemplate redis) {
        return new RedisEscalationScheduler(redis);
    }

    @Bean
    public LeaderElection leaderElection(StringRedisTemplate redis, AlertProperties properties) {
        // A random identity per process. A hostname would be stable across a restart, which is
        // exactly wrong here: a restarted instance would be able to renew the lease its previous
        // incarnation held and had not released.
        return new RedisLeaderElection(redis, UUID.randomUUID().toString(),
                properties.sweep().leaseDuration());
    }

    @Bean
    public EvidenceStore evidenceStore(AlertProperties properties) {
        return new FilesystemEvidenceStore(Path.of(properties.evidence().root()));
    }
}
