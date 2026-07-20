package pk.nust.cuas.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * HTTP security for auth-service.
 *
 * <p>This service is the one that <em>issues</em> tokens, so its own endpoints cannot require one:
 * login, MFA verification, refresh and the JWKS document are necessarily unauthenticated. That
 * makes them the platform's most exposed surface, which is why each is rate limited (section 7.8),
 * records every attempt, and returns a uniform failure that reveals nothing about which part was
 * wrong.
 *
 * <p>Logout is unauthenticated by the same logic: it is authorised by possession of the refresh
 * token being revoked, not by an access token that may already have expired.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // Bearer/opaque-token endpoints with no cookie-based session to protect.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/auth/jwks").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/prometheus").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                            .permitAll()
                        .anyRequest().authenticated());
        return http.build();
    }
}
