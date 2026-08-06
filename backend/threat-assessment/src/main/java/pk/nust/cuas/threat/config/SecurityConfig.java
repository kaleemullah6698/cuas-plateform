package pk.nust.cuas.threat.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import pk.nust.cuas.commons.security.JwtPermissionsConverter;

/**
 * Resource-server security for threat-assessment (ARCHITECTURE.md sections 7.2-7.4).
 *
 * <p>Every endpoint is authenticated; the specific permission is enforced by {@code @PreAuthorize}
 * on each method, which section 7.4 names as the authoritative layer. The actuator probes and the
 * OpenAPI document are open.
 *
 * <p>Scoring itself is off the HTTP surface entirely — a threat score changes only by consuming a
 * track update or an inference result. The single mutating endpoint publishes a scoring policy, and
 * section 7.4 grants that to ADMIN alone.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/actuator/health/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/prometheus").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                            .permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(o -> o
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(new JwtPermissionsConverter())));
        return http.build();
    }
}
