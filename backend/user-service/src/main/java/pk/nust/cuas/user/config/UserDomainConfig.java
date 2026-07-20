package pk.nust.cuas.user.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pk.nust.cuas.user.domain.policy.RoleAssignmentPolicy;
import pk.nust.cuas.user.domain.service.EffectivePermissions;

/**
 * Wires the domain beans. The domain classes carry no Spring annotations, so their construction
 * happens here at the edge (ARCHITECTURE.md section 4.3).
 */
@Configuration
public class UserDomainConfig {

    @Bean
    public RoleAssignmentPolicy roleAssignmentPolicy() {
        return new RoleAssignmentPolicy();
    }

    @Bean
    public EffectivePermissions effectivePermissions() {
        return new EffectivePermissions();
    }
}
