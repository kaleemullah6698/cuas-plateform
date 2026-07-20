package pk.nust.cuas.user.infrastructure.grpc;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import pk.nust.cuas.commons.security.Permission;
import pk.nust.cuas.user.domain.UserRepository;
import pk.nust.cuas.user.domain.model.User;
import pk.nust.cuas.user.domain.service.EffectivePermissions;
import pk.nust.cuas.user.v1.ResolvePermissionsRequest;
import pk.nust.cuas.user.v1.ResolvePermissionsResponse;
import pk.nust.cuas.user.v1.UserServiceGrpc;

/**
 * gRPC surface for permission resolution (ARCHITECTURE.md section 4.1.3).
 *
 * <p>auth-service calls this once, at token issue time, to build the `perms` claim. Resolving
 * there rather than on every request is what lets downstream services authorise from the token
 * alone and keeps user-service off the hot path.
 */
@Component
public class UserGrpcService extends UserServiceGrpc.UserServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(UserGrpcService.class);

    private final UserRepository repository;
    private final EffectivePermissions effectivePermissions;

    public UserGrpcService(UserRepository repository, EffectivePermissions effectivePermissions) {
        this.repository = repository;
        this.effectivePermissions = effectivePermissions;
    }

    @Override
    public void resolvePermissions(ResolvePermissionsRequest request,
                                   StreamObserver<ResolvePermissionsResponse> observer) {
        UUID userId;
        try {
            userId = UUID.fromString(request.getUserId());
        } catch (IllegalArgumentException ex) {
            observer.onError(Status.INVALID_ARGUMENT
                    .withDescription("user_id is not a valid UUID").asRuntimeException());
            return;
        }

        User user = repository.findById(userId).orElse(null);
        if (user == null) {
            observer.onError(Status.NOT_FOUND
                    .withDescription("No such user").asRuntimeException());
            return;
        }

        ResolvePermissionsResponse.Builder response = ResolvePermissionsResponse.newBuilder()
                .setUserId(user.id().toString())
                .setActive(user.isActive());
        user.roles().forEach(role -> response.addRoles(role.name()));
        // A deactivated account resolves to no permissions, so a token cannot be minted for it.
        effectivePermissions.resolve(user).stream()
                .map(Permission::authority)
                .sorted()
                .forEach(response::addPermissions);

        log.debug("Resolved {} permissions for user {}", response.getPermissionsCount(), userId);
        observer.onNext(response.build());
        observer.onCompleted();
    }
}
