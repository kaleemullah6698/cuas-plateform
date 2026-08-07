package pk.nust.cuas.config.api.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import pk.nust.cuas.config.domain.model.ConfigNamespaceKind;

/** The per-kind write permission mapping and its server-side enforcement. */
class ConfigAuthorizerTest {

    private final ConfigAuthorizer authorizer = new ConfigAuthorizer();

    @Test
    void eachKindMapsToItsSection74Permission() {
        assertThat(authorizer.writePermission(ConfigNamespaceKind.POLICY)).isEqualTo("policy:write");
        assertThat(authorizer.writePermission(ConfigNamespaceKind.SENSOR)).isEqualTo("sensor:configure");
        assertThat(authorizer.writePermission(ConfigNamespaceKind.AI)).isEqualTo("ai:configure");
        assertThat(authorizer.writePermission(ConfigNamespaceKind.FEATURE_FLAG)).isEqualTo("config:write");
        assertThat(authorizer.writePermission(ConfigNamespaceKind.RETENTION)).isEqualTo("config:write");
    }

    @Test
    void aCallerHoldingThePermissionMayWrite() {
        Jwt caller = jwt(List.of("policy:read", "policy:write"));

        assertThatCode(() -> authorizer.requireWrite(ConfigNamespaceKind.POLICY, caller))
                .doesNotThrowAnyException();
    }

    @Test
    void aCallerLackingThePermissionIsDenied() {
        Jwt caller = jwt(List.of("policy:read"));

        assertThatThrownBy(() -> authorizer.requireWrite(ConfigNamespaceKind.POLICY, caller))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void aTokenWithNoPermissionsClaimIsDenied() {
        Jwt caller = Jwt.withTokenValue("t").header("alg", "none").subject("u").build();

        assertThatThrownBy(() -> authorizer.requireWrite(ConfigNamespaceKind.AI, caller))
                .isInstanceOf(AccessDeniedException.class);
    }

    private static Jwt jwt(List<String> perms) {
        return Jwt.withTokenValue("t").header("alg", "none").subject("usr-1")
                .claim("perms", perms).issuedAt(Instant.now()).build();
    }
}
