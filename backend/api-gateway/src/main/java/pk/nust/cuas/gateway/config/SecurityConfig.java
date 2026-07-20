package pk.nust.cuas.gateway.config;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import pk.nust.cuas.commons.security.JwtPermissionsConverter;
import pk.nust.cuas.gateway.api.filter.PermissionPreCheckFilter;
import pk.nust.cuas.gateway.api.filter.RateLimitFilter;
import pk.nust.cuas.gateway.common.ProblemAccessDeniedHandler;
import pk.nust.cuas.gateway.common.ProblemAuthenticationEntryPoint;
import pk.nust.cuas.gateway.domain.service.TokenDenylist;
import pk.nust.cuas.gateway.infrastructure.token.AudienceValidator;
import pk.nust.cuas.gateway.infrastructure.token.RevokedTokenValidator;

/**
 * The gateway's authentication and authorisation chain (ARCHITECTURE.md sections 4.1.1, 7.3, 7.4).
 *
 * <p>Tokens are validated against a <em>cached</em> JWKS; the gateway never calls auth-service per
 * request (section 4.1.2). That is what keeps auth-service a small stateless service rather than a
 * synchronous dependency of every call in the platform.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final String jwkSetUri;
    private final String issuer;
    private final String audience;

    public SecurityConfig(
            @Value("${gateway.auth.jwk-set-uri}") String jwkSetUri,
            @Value("${gateway.auth.issuer:https://cuas.nust.edu.pk/auth}") String issuer,
            @Value("${gateway.auth.audience:cuas-api}") String audience) {
        this.jwkSetUri = jwkSetUri;
        this.issuer = issuer;
        this.audience = audience;
    }

    /**
     * @param http                the builder
     * @param rateLimitFilter     section 7.8 enforcement
     * @param preCheckFilter      section 7.4 first-layer enforcement
     * @param entryPoint          renders authentication failures as RFC 7807
     * @param accessDeniedHandler renders authorisation failures as RFC 7807
     * @param jwtDecoder          the JWKS-backed decoder
     * @return the filter chain
     * @throws Exception when the chain cannot be built
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           RateLimitFilter rateLimitFilter,
                                           PermissionPreCheckFilter preCheckFilter,
                                           ProblemAuthenticationEntryPoint entryPoint,
                                           ProblemAccessDeniedHandler accessDeniedHandler,
                                           JwtDecoder jwtDecoder) throws Exception {
        return http
                // Nothing is authenticated by a cookie the browser attaches automatically, so
                // there is no ambient authority for a cross-site request to borrow.
                .csrf(csrf -> csrf.disable())
                // Resolves the bean named corsConfigurationSource below.
                .cors(Customizer.withDefaults())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Authentication endpoints must be reachable without a token; the section
                        // 7.8 login limits are what protects them.
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login",
                                "/api/v1/auth/mfa/verify", "/api/v1/auth/refresh").permitAll()
                        .requestMatchers(HttpMethod.GET, "/auth/jwks").permitAll()
                        // The probes section 4.1.1 names, plus Actuator's own.
                        .requestMatchers(HttpMethod.GET, "/health", "/health/ready").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**",
                                "/actuator/info", "/actuator/prometheus").permitAll()
                        .requestMatchers("/v3/api-docs", "/v3/api-docs/**", "/swagger-ui/**",
                                "/swagger-ui.html").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                        .jwt(jwt -> jwt
                                .decoder(jwtDecoder)
                                .jwtAuthenticationConverter(new JwtPermissionsConverter())))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                // The order is the design. The token is decoded first, so a request can be charged
                // to its user rather than its address; the quota is spent next, so an authorised
                // but excessive caller is still refused; the pre-check runs last, so a 403 is only
                // ever produced for a caller who has been positively identified.
                .addFilterAfter(rateLimitFilter, BearerTokenAuthenticationFilter.class)
                .addFilterBefore(preCheckFilter, AuthorizationFilter.class)
                .build();
    }

    /**
     * The JWKS-backed decoder of section 7.3.
     *
     * <p>Key rotation needs neither configuration nor a restart. Nimbus refetches the key set when
     * it meets a {@code kid} it does not recognise, so a newly rotated signing key is picked up on
     * first use while the previous key stays cached for the overlap window in which tokens signed
     * by it are still live. The refetch is rate-limited internally, so an unknown {@code kid} in a
     * forged token cannot be used to hammer auth-service.
     *
     * @param denylist the revocation denylist consulted on every decode
     * @return the decoder
     */
    @Bean
    public JwtDecoder jwtDecoder(TokenDenylist denylist) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri)
                .cache(new ConcurrentMapCache("jwks"))
                .build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(validators(denylist)));
        return decoder;
    }

    private List<OAuth2TokenValidator<Jwt>> validators(TokenDenylist denylist) {
        return List.of(
                JwtValidators.createDefaultWithIssuer(issuer),
                new AudienceValidator(audience),
                new RevokedTokenValidator(denylist));
    }

    /**
     * @param allowedOrigins origins permitted to call the API; empty by default, so a deployment
     *                       that forgets to configure them serves no browser rather than all of them
     * @return the CORS policy
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${gateway.cors.allowed-origins:}") List<String> allowedOrigins) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(
                List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(
                List.of("Authorization", "Content-Type", "X-Correlation-Id"));
        configuration.setExposedHeaders(List.of("X-Correlation-Id", "X-RateLimit-Limit",
                "X-RateLimit-Remaining", "X-RateLimit-Reset", "Retry-After"));
        // The refresh token is an HttpOnly cookie (section 7.3), so the browser must be permitted
        // to send credentials on the refresh call.
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
