package pk.nust.cuas.audit.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pk.nust.cuas.audit.domain.AuditRepository;
import pk.nust.cuas.audit.domain.service.AuditAppendService;
import pk.nust.cuas.audit.domain.service.ChainVerifier;

/**
 * Wires the domain services.
 *
 * <p>The domain classes carry no Spring annotations — they must compile without Spring on the
 * classpath (ARCHITECTURE.md section 4.3) — so their beans are declared here, at the edge, where
 * framework knowledge belongs.
 */
@Configuration
public class AuditDomainConfig {

    @Bean
    public AuditAppendService auditAppendService(AuditRepository repository) {
        return new AuditAppendService(repository);
    }

    @Bean
    public ChainVerifier chainVerifier() {
        return new ChainVerifier();
    }
}
