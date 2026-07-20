package pk.nust.cuas.user.config;

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
 * Resource-server security for user-service (ARCHITECTURE.md sections 7.2-7.4).
 *
 * <p>RBAC is active from the first endpoint; the specific permission for each operation is
 * enforced by {@code @PreAuthorize}, which is the authoritative layer per section 7.4.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    /** A violated separation-of-duty invariant fails startup rather than reaching production. */
    public SecurityConfig() {
        Role.assertSeparationOfDuty();
    }

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
