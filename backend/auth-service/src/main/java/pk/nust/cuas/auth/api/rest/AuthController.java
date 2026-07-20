package pk.nust.cuas.auth.api.rest;

import com.nimbusds.jose.jwk.JWKSet;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pk.nust.cuas.auth.application.AuthenticationService;
import pk.nust.cuas.auth.domain.exception.InvalidRefreshTokenException;
import pk.nust.cuas.auth.infrastructure.token.JwtKeyProvider;

/**
 * Authentication endpoints (ARCHITECTURE.md sections 4.1.2, 7.3).
 *
 * <p>The refresh token is returned in an {@code HttpOnly}, {@code Secure}, {@code SameSite=Strict}
 * cookie and never in the response body (section 7.3). The access token <em>is</em> in the body,
 * because the browser keeps it in memory only — putting it in a readable cookie or in storage is
 * what makes an XSS foothold into a stolen session.
 */
@RestController
@Tag(name = "Authentication", description = "Login, MFA, token rotation, and the JWKS document")
public class AuthController {

    /** Cookie carrying the refresh token. */
    static final String REFRESH_COOKIE = "cuas_refresh";

    private final AuthenticationService authentication;
    private final JwtKeyProvider keyProvider;
    private final boolean secureCookies;

    public AuthController(AuthenticationService authentication, JwtKeyProvider keyProvider,
                          @org.springframework.beans.factory.annotation.Value(
                                  "${auth.cookie.secure:true}") boolean secureCookies) {
        this.authentication = authentication;
        this.keyProvider = keyProvider;
        this.secureCookies = secureCookies;
    }

    @PostMapping("/api/v1/auth/login")
    @Operation(summary = "Verify username and password",
            description = "Returns an MFA challenge when the account has a second factor "
                    + "enabled, otherwise issues tokens directly.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Authenticated, or MFA required"),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "401", description = "Invalid credentials"),
            @ApiResponse(responseCode = "423", description = "Account temporarily locked")})
    public ResponseEntity<Map<String, Object>> login(@Valid @RequestBody LoginRequest request,
                                                     HttpServletResponse response) {
        AuthenticationService.LoginResult result =
                authentication.login(request.username(), request.password());

        if (result.mfaRequired()) {
            return ResponseEntity.ok(Map.of(
                    "mfaRequired", true,
                    "challengeId", result.challengeId().toString()));
        }
        return tokenResponse(result.tokens(), response);
    }

    @PostMapping("/api/v1/auth/mfa/verify")
    @Operation(summary = "Verify the second factor and complete the login")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Authenticated"),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "401", description = "Invalid or expired code")})
    public ResponseEntity<Map<String, Object>> verifyMfa(@Valid @RequestBody MfaRequest request,
                                                         HttpServletResponse response) {
        return tokenResponse(authentication.verifyMfa(request.challengeId(), request.code()),
                response);
    }

    @PostMapping("/api/v1/auth/refresh")
    @Operation(summary = "Rotate the token pair",
            description = "Presenting an already-exchanged token is treated as theft: the whole "
                    + "token family is revoked and the request refused.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Rotated"),
            @ApiResponse(responseCode = "401", description = "Unknown, expired, revoked or reused")})
    public ResponseEntity<Map<String, Object>> refresh(
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshCookie,
            HttpServletResponse response) {
        if (refreshCookie == null || refreshCookie.isBlank()) {
            throw new InvalidRefreshTokenException();
        }
        return tokenResponse(authentication.refresh(refreshCookie), response);
    }

    @PostMapping("/api/v1/auth/logout")
    @Operation(summary = "End this session",
            description = "Authorised by possession of the refresh token, not by an access token, "
                    + "which may already have expired.")
    @ApiResponse(responseCode = "204", description = "Session ended, or nothing to end")
    public ResponseEntity<Void> logout(
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshCookie,
            HttpServletResponse response) {
        if (refreshCookie != null && !refreshCookie.isBlank()) {
            authentication.logout(refreshCookie);
        }
        clearRefreshCookie(response);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/auth/jwks")
    @Operation(summary = "Public keys for verifying access tokens",
            description = "Fetched and cached by the gateway and every service, so token "
                    + "validation never calls this service per request (section 7.3).")
    @ApiResponse(responseCode = "200", description = "The JWKS document; public material only")
    public ResponseEntity<Map<String, Object>> jwks() {
        JWKSet publicKeys = keyProvider.publicJwkSet();
        return ResponseEntity.ok()
                .cacheControl(org.springframework.http.CacheControl.maxAge(Duration.ofMinutes(5)))
                .body(publicKeys.toJSONObject());
    }

    private ResponseEntity<Map<String, Object>> tokenResponse(
            AuthenticationService.TokenPair tokens, HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie(tokens).toString());
        return ResponseEntity.ok(Map.of(
                "accessToken", tokens.accessToken(),
                "tokenType", "Bearer",
                "expiresAt", tokens.accessTokenExpiresAt().toString(),
                "sessionId", tokens.sessionId().toString()));
    }

    private ResponseCookie refreshCookie(AuthenticationService.TokenPair tokens) {
        return ResponseCookie.from(REFRESH_COOKIE, tokens.refreshToken())
                .httpOnly(true)                 // unreadable from JavaScript, so XSS cannot lift it
                .secure(secureCookies)          // never sent over plaintext outside local dev
                .sameSite("Strict")             // not attached to cross-site requests
                .path("/api/v1/auth")           // sent only to the endpoints that need it
                .maxAge(Duration.between(Instant.now(), tokens.refreshTokenExpiresAt()))
                .build();
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(REFRESH_COOKIE, "")
                .httpOnly(true).secure(secureCookies).sameSite("Strict")
                .path("/api/v1/auth").maxAge(0).build().toString());
    }

    /** Credentials presented at login. */
    public record LoginRequest(
            @NotBlank @Size(max = 64) String username,
            @NotBlank @Size(max = 200) String password) {
    }

    /** The second factor. */
    public record MfaRequest(
            @NotNull UUID challengeId,
            @NotBlank @Pattern(regexp = "^[0-9]{6}$", message = "must be six digits") String code) {
    }
}
