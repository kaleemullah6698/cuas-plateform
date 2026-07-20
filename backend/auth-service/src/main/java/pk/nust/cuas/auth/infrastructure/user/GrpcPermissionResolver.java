package pk.nust.cuas.auth.infrastructure.user;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import pk.nust.cuas.user.v1.ResolvePermissionsRequest;
import pk.nust.cuas.user.v1.ResolvePermissionsResponse;
import pk.nust.cuas.user.v1.UserServiceGrpc;

/**
 * Resolves permissions by calling user-service over gRPC (ARCHITECTURE.md section 4.1.3).
 *
 * <p>This is the one synchronous dependency auth-service has. It sits on the login path, so it is
 * given a short deadline: a slow user-service should fail a login quickly rather than hold the
 * request open. A failure here refuses the login rather than issuing a token with no permissions,
 * because a token that authenticates but authorises nothing looks to the operator like a broken
 * account rather than an outage, and would be indistinguishable from a genuine loss of authority.
 */
@Component
@ConditionalOnProperty(name = "auth.user-service.enabled", havingValue = "true",
        matchIfMissing = true)
public class GrpcPermissionResolver implements PermissionResolver, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(GrpcPermissionResolver.class);

    private final ManagedChannel channel;
    private final UserServiceGrpc.UserServiceBlockingStub stub;
    private final long deadlineMillis;

    public GrpcPermissionResolver(
            @Value("${auth.user-service.host:localhost}") String host,
            @Value("${auth.user-service.port:9082}") int port,
            @Value("${auth.user-service.deadline-ms:2000}") long deadlineMillis) {
        // Plaintext here; section 7.2 places mTLS between services, which is introduced with the
        // certificate infrastructure rather than hard-coded per client.
        this.channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
        this.stub = UserServiceGrpc.newBlockingStub(channel);
        this.deadlineMillis = deadlineMillis;
        log.info("Permission resolver targeting user-service at {}:{}", host, port);
    }

    @Override
    public Resolution resolve(UUID userId) {
        try {
            ResolvePermissionsResponse response = stub
                    .withDeadlineAfter(deadlineMillis, TimeUnit.MILLISECONDS)
                    .resolvePermissions(ResolvePermissionsRequest.newBuilder()
                            .setUserId(userId.toString())
                            .build());

            return new Resolution(response.getRolesList(), response.getPermissionsList(),
                    response.getActive());
        } catch (StatusRuntimeException ex) {
            if (ex.getStatus().getCode() == Status.Code.NOT_FOUND) {
                // Authenticated against a credential whose account no longer exists in userdb.
                log.warn("user-service has no account for {}", userId);
                return new Resolution(java.util.List.of(), java.util.List.of(), false);
            }
            throw new IllegalStateException(
                    "Could not resolve permissions for " + userId + "; refusing to issue a token",
                    ex);
        }
    }

    @Override
    public void close() {
        channel.shutdown();
        try {
            if (!channel.awaitTermination(5, TimeUnit.SECONDS)) {
                channel.shutdownNow();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            channel.shutdownNow();
        }
    }
}
