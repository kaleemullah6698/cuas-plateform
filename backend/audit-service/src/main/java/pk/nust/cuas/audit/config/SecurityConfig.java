package pk.nust.cuas.audit.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import pk.nust.cuas.commons.security.JwtPermissionsConverter;
import pk.nust.cuas.commons.security.Role;

/**
 * Resource-server security for audit-service (ARCHITECTURE.md sections 7.2–7.4).
 *
 * <p>RBAC is wired from the outset rather than retrofitted. Access tokens are validated against
 * the issuer's JWKS, and the {@code perms} claim becomes the authority set that
 * {@code @PreAuthorize} evaluates. The decoder resolves JWKS lazily on first use, so this service
 * starts independently of auth-service — it simply rejects tokens until the issuer is reachable,
 * which is the correct failure direction.
 *
 * <p>Stateless by design: no session is created, so there is no session-fixation surface and any
 * instance can serve any request.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    /**
     * Fails startup if the separation-of-duty invariants of section 7.4 have been violated,
     * rather than letting a widened ADMIN role reach production.
     */
    public SecurityConfig() {
        Role.assertSeparationOfDuty();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // No browser-driven state changes reach this service; tokens are bearer-only,
                // so CSRF protection has nothing to protect and would only reject valid calls.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Liveness and readiness must answer before a token can be obtained.
                        .requestMatchers(HttpMethod.GET, "/actuator/health/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/prometheus").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                            .permitAll()
                        // Everything else requires a valid token; the specific permission is
                        // enforced per endpoint by @PreAuthorize, which is authoritative.
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(new JwtPermissionsConverter())));
        return http.build();
    }
}
