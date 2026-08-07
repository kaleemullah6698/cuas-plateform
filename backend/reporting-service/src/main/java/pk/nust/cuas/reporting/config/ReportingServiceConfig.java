package pk.nust.cuas.reporting.config;

import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.concurrent.Executor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import pk.nust.cuas.commons.id.Uuidv7Generator;
import pk.nust.cuas.reporting.domain.port.ArtifactStore;
import pk.nust.cuas.reporting.infrastructure.artifact.FilesystemArtifactStore;

/**
 * Wires reporting-service's shared beans and the asynchronous generation pool.
 *
 * <p>Generation runs on a bounded {@code reportExecutor} so a burst of requests cannot exhaust the
 * process — the queue caps how many jobs wait, and the pool caps how many run at once. The 202 has
 * already been returned by the time a job reaches the pool, so back-pressure here never blocks a
 * request thread.
 */
@Configuration
@EnableAsync
@EnableConfigurationProperties(ReportingProperties.class)
public class ReportingServiceConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public Uuidv7Generator uuidv7Generator() {
        return new Uuidv7Generator(new SecureRandom());
    }

    @Bean
    public ArtifactStore artifactStore(ReportingProperties properties) {
        return new FilesystemArtifactStore(Path.of(properties.artifactRoot()));
    }

    @Bean("reportExecutor")
    public Executor reportExecutor(ReportingProperties properties) {
        ReportingProperties.Worker worker = properties.worker();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(worker.coreThreads());
        executor.setMaxPoolSize(worker.maxThreads());
        executor.setQueueCapacity(worker.queueCapacity());
        executor.setThreadNamePrefix("report-");
        executor.initialize();
        return executor;
    }
}
