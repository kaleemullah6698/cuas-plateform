package pk.nust.cuas.user.api.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pk.nust.cuas.commons.security.Permission;
import pk.nust.cuas.user.api.dto.CreateUserRequest;
import pk.nust.cuas.user.api.dto.RoleChangeRequest;
import pk.nust.cuas.user.api.dto.UpdateUserRequest;
import pk.nust.cuas.user.api.dto.UserResponse;
import pk.nust.cuas.user.application.UserApplicationService;
import pk.nust.cuas.user.domain.UserRepository;

/**
 * User and role administration (ARCHITECTURE.md sections 4.1.3, 7.4).
 *
 * <p>Every mutating endpoint is gated by a permission from the section 7.4 matrix and produces an
 * audit record through the application service. The acting user's own permissions are read from
 * their token and passed into the domain, because the self-escalation rule is a comparison between
 * what the actor holds and what they are trying to grant — a check that cannot be made without
 * both sides.
 */
@RestController
@RequestMapping("/api/v1/users")
@Validated
@Tag(name = "Users", description = "User accounts and role assignment")
public class UserController {

    private final UserApplicationService users;
    private final UserRepository repository;

    public UserController(UserApplicationService users, UserRepository repository) {
        this.users = users;
        this.repository = repository;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('user:read')")
    @Operation(summary = "List user accounts")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Accounts, ordered by username"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token"),
            @ApiResponse(responseCode = "403", description = "Caller lacks user:read")})
    public ResponseEntity<List<UserResponse>> list(
            @RequestParam(defaultValue = "0") @Min(0) int offset,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit) {
        return ResponseEntity.ok(repository.findAll(offset, limit).stream()
                .map(UserResponse::from).toList());
    }

    @GetMapping("/{userId}")
    @PreAuthorize("hasAuthority('user:read')")
    @Operation(summary = "Fetch one account")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The account"),
            @ApiResponse(responseCode = "403", description = "Caller lacks user:read"),
            @ApiResponse(responseCode = "404", description = "No such account")})
    public ResponseEntity<UserResponse> get(@PathVariable UUID userId) {
        return ResponseEntity.ok(UserResponse.from(users.require(userId)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('user:write')")
    @Operation(summary = "Create an account",
            description = "The account is created with no roles; authority is granted separately.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Created"),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "403", description = "Caller lacks user:write"),
            @ApiResponse(responseCode = "409", description = "Username or email already registered")})
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request,
                                               Authentication authentication) {
        var created = users.createUser(request.username(), request.email(),
                request.displayName(), actor(authentication));
        return ResponseEntity.created(URI.create("/api/v1/users/" + created.id()))
                .body(UserResponse.from(created));
    }

    @PatchMapping("/{userId}")
    @PreAuthorize("hasAuthority('user:write')")
    @Operation(summary = "Update a profile")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Updated"),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "403", description = "Caller lacks user:write"),
            @ApiResponse(responseCode = "404", description = "No such account")})
    public ResponseEntity<UserResponse> update(@PathVariable UUID userId,
                                               @Valid @RequestBody UpdateUserRequest request,
                                               Authentication authentication) {
        return ResponseEntity.ok(UserResponse.from(users.updateProfile(
                userId, request.email(), request.displayName(), actor(authentication))));
    }

    @PostMapping("/{userId}/roles")
    @PreAuthorize("hasAuthority('role:write')")
    @Operation(summary = "Grant a role",
            description = "Refused with 403 SELF_ESCALATION_DENIED when the caller does not "
                    + "themselves hold every permission the role confers (section 7.4).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Granted"),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "403", description = "Caller lacks role:write, or the "
                    + "grant would escalate the caller's own authority"),
            @ApiResponse(responseCode = "404", description = "No such account"),
            @ApiResponse(responseCode = "409", description = "Separation of duty violated")})
    public ResponseEntity<UserResponse> assignRole(@PathVariable UUID userId,
                                                   @Valid @RequestBody RoleChangeRequest request,
                                                   Authentication authentication) {
        return ResponseEntity.ok(UserResponse.from(users.assignRole(
                userId, request.role(), request.justification(), actor(authentication))));
    }

    @DeleteMapping("/{userId}/roles")
    @PreAuthorize("hasAuthority('role:write')")
    @Operation(summary = "Revoke a role",
            description = "Revocation narrows authority, so no escalation check applies — but it "
                    + "is still justified and audited.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Revoked"),
            @ApiResponse(responseCode = "403", description = "Caller lacks role:write"),
            @ApiResponse(responseCode = "404", description = "No such account")})
    public ResponseEntity<UserResponse> revokeRole(@PathVariable UUID userId,
                                                   @Valid @RequestBody RoleChangeRequest request,
                                                   Authentication authentication) {
        return ResponseEntity.ok(UserResponse.from(users.revokeRole(
                userId, request.role(), request.justification(), actor(authentication))));
    }

    @DeleteMapping("/{userId}")
    @PreAuthorize("hasAuthority('user:write')")
    @Operation(summary = "Deactivate an account",
            description = "Accounts are deactivated, never deleted, so audit references remain "
                    + "resolvable. Announces the change so sessions are revoked.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Deactivated"),
            @ApiResponse(responseCode = "403", description = "Caller lacks user:write"),
            @ApiResponse(responseCode = "404", description = "No such account")})
    public ResponseEntity<UserResponse> deactivate(@PathVariable UUID userId,
                                                   Authentication authentication) {
        return ResponseEntity.ok(UserResponse.from(users.deactivate(userId, actor(authentication))));
    }

    /**
     * Builds the acting identity from the validated token.
     *
     * <p>The permissions come from the token's authorities rather than from a fresh lookup: the
     * token is what the caller actually presented, and re-resolving could silently grant them
     * authority they did not hold when the request was authenticated.
     */
    private UserApplicationService.Actor actor(Authentication authentication) {
        Set<Permission> held = EnumSet.noneOf(Permission.class);
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            Permission.fromAuthority(authority.getAuthority()).ifPresent(held::add);
        }
        return new UserApplicationService.Actor(actorId(authentication), held);
    }

    private UUID actorId(Authentication authentication) {
        try {
            return UUID.fromString(authentication.getName());
        } catch (IllegalArgumentException ex) {
            // Tokens issued by auth-service carry the user id as subject. A non-UUID subject means
            // a service or test principal; a fixed nil id keeps the audit record well-formed.
            return new UUID(0L, 0L);
        }
    }
}
