package pk.nust.cuas.user.domain.exception;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import pk.nust.cuas.commons.exception.DomainException;
import pk.nust.cuas.commons.security.Permission;
import pk.nust.cuas.commons.security.Role;

/**
 * An actor attempted to grant authority they do not themselves hold (ARCHITECTURE.md 7.4).
 *
 * <p>Surfaces as {@code 403 SELF_ESCALATION_DENIED}, the code named in section 7.4. The message
 * names the permissions that were missing so a legitimate administrator understands the refusal,
 * without revealing anything the actor could not already enumerate about their own grants.
 */
public class SelfEscalationDeniedException extends DomainException {

    public static final String CODE = "SELF_ESCALATION_DENIED";

    public SelfEscalationDeniedException(UUID actorId, Role role, List<Permission> missing) {
        super(CODE, HttpStatus.FORBIDDEN,
                "You cannot grant %s because it includes permissions you do not hold: %s"
                        .formatted(role, missing.stream().map(Permission::authority)
                                .collect(Collectors.joining(", "))),
                Map.of("role", role.name(),
                        "missingPermissions", missing.stream().map(Permission::authority).toList(),
                        "actorId", actorId.toString()),
                null);
    }
}
