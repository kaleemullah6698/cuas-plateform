package pk.nust.cuas.config.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import pk.nust.cuas.commons.security.JwtPermissionsConverter;

/**
 * Resource-server security for config-service (ARCHITECTURE.md §7.2–§7.4).
 *
 * <p>Every REST endpoint is authenticated; reads require {@code policy:read} and writes the kind's
 * §7.4 permission, enforced by {@code @PreAuthorize} and {@link pk.nust.cuas.config.api.rest.ConfigAuthorizer}.
 * The actuator probes and the OpenAPI document are open. The gRPC {@code WatchConfig} surface is
 * authorised at the transport by mTLS (§7.2), not by this filter chain.
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
