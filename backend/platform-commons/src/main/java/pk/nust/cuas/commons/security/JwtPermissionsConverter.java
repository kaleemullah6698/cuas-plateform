package pk.nust.cuas.commons.security;

import java.util.Collection;
import java.util.List;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Turns the {@code perms} claim of a platform access token into Spring Security authorities.
 *
 * <p>The platform authorises on <em>permissions</em>, not roles (ARCHITECTURE.md section 7.4):
 * the token carries a flat, already-resolved {@code perms} array, so a service never has to
 * expand roles itself and every {@code @PreAuthorize("hasAuthority('audit:read')")} compares
 * against exactly what the issuer granted.
 *
 * <p>Unknown permission strings are dropped rather than granted. A token naming a permission this
 * build does not know about must not confer authority — silently ignoring it fails closed.
 */
public final class JwtPermissionsConverter
        implements Converter<Jwt, AbstractAuthenticationToken> {

    /** Claim carrying the resolved permission list (section 7.3). */
    public static final String PERMISSIONS_CLAIM = "perms";

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        return new JwtAuthenticationToken(jwt, authorities(jwt), jwt.getSubject());
    }

    private Collection<GrantedAuthority> authorities(Jwt jwt) {
        List<String> claim = jwt.getClaimAsStringList(PERMISSIONS_CLAIM);
        if (claim == null) {
            return List.of();
        }
        return claim.stream()
                .map(Permission::fromAuthority)
                .flatMap(java.util.Optional::stream)
                .map(permission -> (GrantedAuthority) new SimpleGrantedAuthority(permission.authority()))
                .toList();
    }
}
