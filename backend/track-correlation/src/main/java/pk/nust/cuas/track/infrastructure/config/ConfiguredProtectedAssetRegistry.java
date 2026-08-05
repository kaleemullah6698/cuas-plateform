package pk.nust.cuas.track.infrastructure.config;

import java.util.List;
import pk.nust.cuas.track.domain.model.ProtectedAsset;
import pk.nust.cuas.track.domain.port.ProtectedAssetRegistry;

/**
 * Supplies protected assets from this service's own configuration — the interim adapter behind
 * {@link ProtectedAssetRegistry} (ARCHITECTURE.md sections 4.1.5, 4.1.12).
 *
 * <p>ARCHITECTURE.md section 4.1.12 assigns protected-asset geometry to the Configuration Service,
 * which is built at M8. Until then the list comes from {@code correlation.assets}. The list is
 * resolved once at construction and held immutable, because a static config list cannot change
 * without a restart anyway and pretending otherwise would invite a caller to poll it.
 *
 * <p>Replacing this with the config-service-backed adapter at M8 is a one-class change with a
 * {@code WatchConfig} subscription behind it. Nothing in the domain, the CPA calculation, or the
 * published contract moves — which is why the registry was declared as a port in the first place.
 */
public class ConfiguredProtectedAssetRegistry implements ProtectedAssetRegistry {

    private final List<ProtectedAsset> assets;

    public ConfiguredProtectedAssetRegistry(List<ProtectedAsset> assets) {
        this.assets = List.copyOf(assets);
    }

    @Override
    public List<ProtectedAsset> all() {
        return assets;
    }
}
