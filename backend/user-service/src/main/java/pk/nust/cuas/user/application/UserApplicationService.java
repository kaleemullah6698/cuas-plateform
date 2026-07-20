package pk.nust.cuas.user.application;

import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pk.nust.cuas.commons.audit.ActorType;
import pk.nust.cuas.commons.audit.AuditActions;
import pk.nust.cuas.commons.audit.AuditEvent;
import pk.nust.cuas.commons.audit.AuditOutcome;
import pk.nust.cuas.commons.audit.AuditPublisher;
import pk.nust.cuas.commons.correlation.CorrelationId;
import pk.nust.cuas.commons.security.Permission;
import pk.nust.cuas.commons.security.Role;
import pk.nust.cuas.user.domain.UserRepository;
import pk.nust.cuas.user.domain.exception.SelfEscalationDeniedException;
import pk.nust.cuas.user.domain.exception.UserNotFoundException;
import pk.nust.cuas.user.domain.exception.UsernameAlreadyExistsException;
import pk.nust.cuas.user.domain.model.User;
import pk.nust.cuas.user.domain.policy.RoleAssignmentPolicy;
import pk.nust.cuas.user.infrastructure.messaging.UserEventPublisher;

/**
 * Orchestrates user and role changes, and records every one of them.
 *
 * <p>This is the single place where a mutation is paired with its audit event. Section 7.5
 * requires the publish to be <em>fail-closed</em>: {@link AuditPublisher#publish} is synchronous
 * and throws, and because these methods are {@code @Transactional} that exception rolls the change
 * back. An action that cannot be recorded therefore does not happen — the audit event is published
 * <em>before</em> the transaction commits, deliberately, so ordering cannot be reversed.
 *
 * <p>The application layer holds no business rules of its own: they live in
 * {@link RoleAssignmentPolicy} and {@link User}. What lives here is sequencing — check, mutate,
 * record, publish.
 */
@Service
public class UserApplicationService {

    private static final Logger log = LoggerFactory.getLogger(UserApplicationService.class);

    private static final String RESOURCE_USER = "USER";

    private final UserRepository repository;
    private final RoleAssignmentPolicy roleAssignmentPolicy;
    private final AuditPublisher auditPublisher;
    private final UserEventPublisher userEventPublisher;

    public UserApplicationService(UserRepository repository,
                                  RoleAssignmentPolicy roleAssignmentPolicy,
                                  AuditPublisher auditPublisher,
                                  UserEventPublisher userEventPublisher) {
        this.repository = repository;
        this.roleAssignmentPolicy = roleAssignmentPolicy;
        this.auditPublisher = auditPublisher;
        this.userEventPublisher = userEventPublisher;
    }

    /**
     * Creates a new account with no roles.
     *
     * @param username    the login name
     * @param email       the contact address
     * @param displayName the name shown in the console
     * @param actor       who is creating it
     * @return the created user
     */
    @Transactional
    public User createUser(String username, String email, String displayName, Actor actor) {
        if (repository.existsByUsername(username)) {
            throw new UsernameAlreadyExistsException("username", username);
        }
        if (repository.existsByEmail(email)) {
            throw new UsernameAlreadyExistsException("email", email);
        }

        User created = repository.save(User.create(username, email, displayName));

        audit(AuditActions.USER_CREATED, created.id().toString(), actor, AuditOutcome.SUCCESS,
                java.util.Map.of("username", username));
        log.info("User {} created by {}", created.id(), actor.id());
        return created;
    }

    /**
     * Updates the mutable profile fields of an account.
     */
    @Transactional
    public User updateProfile(UUID userId, String email, String displayName, Actor actor) {
        User existing = require(userId);
        User updated = repository.save(existing.withProfile(email, displayName));

        audit(AuditActions.USER_UPDATED, userId.toString(), actor, AuditOutcome.SUCCESS,
                java.util.Map.of(
                        "before", "email=%s,displayName=%s".formatted(existing.email(), existing.displayName()),
                        "after", "email=%s,displayName=%s".formatted(updated.email(), updated.displayName())));
        return updated;
    }

    /**
     * Assigns a role, enforcing the section 7.4 constraints first.
     *
     * <p>A refused attempt is audited with outcome {@code DENIED} before the exception propagates:
     * an attempted escalation is precisely the event a reviewer needs to see, and recording only
     * successful changes would hide it.
     *
     * @param userId        the user to change
     * @param role          the role to grant
     * @param justification why — required by section 7.4, recorded before the change commits
     * @param actor         who is making the change, with their own effective permissions
     * @return the updated user
     */
    @Transactional
    public User assignRole(UUID userId, Role role, String justification, Actor actor) {
        User target = require(userId);

        try {
            roleAssignmentPolicy.checkAssignable(actor.id(), actor.permissions(), target, role);
        } catch (SelfEscalationDeniedException ex) {
            audit(AuditActions.SELF_ESCALATION_DENIED, userId.toString(), actor,
                    AuditOutcome.DENIED, java.util.Map.of("role", role.name()));
            throw ex;
        } catch (RuntimeException ex) {
            audit(AuditActions.ROLE_ASSIGNED, userId.toString(), actor,
                    AuditOutcome.DENIED, java.util.Map.of("role", role.name(),
                            "reason", String.valueOf(ex.getMessage())));
            throw ex;
        }

        // Section 7.4: the justification is recorded before the change commits.
        repository.recordRoleChange(userId, role.name(), "ASSIGNED", justification,
                actor.id().toString());
        User updated = repository.save(target.withRole(role));

        audit(AuditActions.ROLE_ASSIGNED, userId.toString(), actor, AuditOutcome.SUCCESS,
                java.util.Map.of("role", role.name(), "justification", justification));
        log.info("Role {} assigned to {} by {}", role, userId, actor.id());
        return updated;
    }

    /**
     * Revokes a role. Revocation narrows authority, so it needs no escalation check — but it is
     * still justified and audited.
     */
    @Transactional
    public User revokeRole(UUID userId, Role role, String justification, Actor actor) {
        User target = require(userId);

        repository.recordRoleChange(userId, role.name(), "REVOKED", justification,
                actor.id().toString());
        User updated = repository.save(target.withoutRole(role));

        audit(AuditActions.ROLE_REVOKED, userId.toString(), actor, AuditOutcome.SUCCESS,
                java.util.Map.of("role", role.name(), "justification", justification));
        return updated;
    }

    /**
     * Deactivates an account and announces it, so auth-service can revoke its sessions and
     * refresh-token families (section 4.1.2). Without that announcement a deactivated user would
     * keep acting until their access token expired.
     */
    @Transactional
    public User deactivate(UUID userId, Actor actor) {
        User target = require(userId);
        User updated = repository.save(target.deactivated());

        audit(AuditActions.USER_DEACTIVATED, userId.toString(), actor, AuditOutcome.SUCCESS,
                java.util.Map.of("username", target.username()));
        userEventPublisher.publishDeactivated(userId, actor.id().toString());

        log.info("User {} deactivated by {}", userId, actor.id());
        return updated;
    }

    /**
     * @param userId the user to load
     * @return the user
     * @throws UserNotFoundException when no such user exists
     */
    @Transactional(readOnly = true)
    public User require(UUID userId) {
        return repository.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));
    }

    private void audit(String action, String resourceId, Actor actor, AuditOutcome outcome,
                       java.util.Map<String, String> details) {
        auditPublisher.publish(AuditEvent.of(action, RESOURCE_USER)
                .actor(actor.id().toString(), ActorType.USER)
                .resourceId(resourceId)
                .outcome(outcome)
                .details(details)
                .correlationId(CorrelationId.current().orElse(null))
                .build());
    }

    /**
     * The authenticated caller performing a change.
     *
     * @param id          the acting user's id
     * @param permissions the permissions they actually hold, used for the escalation check
     */
    public record Actor(UUID id, Set<Permission> permissions) {
    }
}
