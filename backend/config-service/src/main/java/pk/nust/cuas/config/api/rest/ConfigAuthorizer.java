package pk.nust.cuas.config.api.rest;

import java.util.List;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import pk.nust.cuas.config.domain.model.ConfigNamespaceKind;

/**
 * Maps a configuration change to the §7.4 permission that authorises it, and enforces it server-side.
 *
 * <p><b>Writes are gated per namespace kind, using only permissions from the §7.4 matrix</b> — a
 * policy retune is {@code policy:write}, registering a sensor is {@code sensor:configure}, changing an
 * AI endpoint or model version is {@code ai:configure}, and the remaining families (feature flags,
 * retention, protected assets, general) are the generic {@code config:write}. No permission is
 * invented, which §0 forbids.
 *
 * <p>The permission is checked against the token's {@code perms} claim (§7.3) here rather than by a
 * static {@code @PreAuthorize} string, because the kind that decides the permission is known only at
 * request time — from the body on a write, from the stored namespace on a rollback. The check is
 * authoritative and server-side; the endpoint is additionally {@code @PreAuthorize("isAuthenticated()")}.
 */
@Component
public class ConfigAuthorizer {

    /**
     * Verifies the caller may change a namespace of this kind.
     *
     * @param kind   the namespace family
     * @param caller the authenticated token
     * @throws AccessDeniedException if the caller lacks the required permission
     */
    public void requireWrite(ConfigNamespaceKind kind, Jwt caller) {
        String required = writePermission(kind);
        List<String> held = caller.getClaimAsStringList("perms");
        if (held == null || !held.contains(required)) {
            throw new AccessDeniedException("configuration change requires " + required);
        }
    }

    /** The §7.4 permission that authorises a write to this namespace kind. */
    public String writePermission(ConfigNamespaceKind kind) {
        return switch (kind) {
            case POLICY -> "policy:write";
            case SENSOR -> "sensor:configure";
            case AI -> "ai:configure";
            case FEATURE_FLAG, RETENTION, PROTECTED_ASSET, GENERAL -> "config:write";
        };
    }
}
