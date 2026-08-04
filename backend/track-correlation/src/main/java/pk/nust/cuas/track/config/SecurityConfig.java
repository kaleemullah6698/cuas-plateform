package pk.nust.cuas.track.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import pk.nust.cuas.commons.security.JwtPermissionsConverter;

/**
 * Resource-server security for track-correlation (ARCHITECTURE.md sections 7.2-7.4).
 *
 * <p>The {@code GET /tracks} read API is operator-facing, so it is authenticated and the specific
 * permission ({@code track:read}) is enforced by {@code @PreAuthorize} — the authoritative layer of
 * section 7.4. The actuator probes and the OpenAPI document are open. There are no mutating
 * endpoints: tracks change only by consuming detections, off the HTTP surface.
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
