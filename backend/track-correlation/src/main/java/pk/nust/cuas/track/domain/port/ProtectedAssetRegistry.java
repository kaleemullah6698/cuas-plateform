package pk.nust.cuas.track.domain.port;

import java.util.List;
import pk.nust.cuas.track.domain.model.ProtectedAsset;

/**
 * The protected assets this deployment defends, as CPA needs them (ARCHITECTURE.md section 4.1.5).
 *
 * <p>ARCHITECTURE.md section 4.1.12 assigns protected-asset geometry to the Configuration Service.
 * That service does not exist until M8, so this port exists to keep the dependency direction right
 * in the meantime: the domain declares what it needs, and the adapter that satisfies it is an
 * infrastructure detail. Today that adapter reads a static list from this service's configuration;
 * at M8 it becomes a config-service-backed adapter refreshed by {@code WatchConfig}. Neither the CPA
 * calculation nor the published contract changes when it is swapped — which is the whole point of
 * declaring it as a port rather than reaching for another service's table.
 */
public interface ProtectedAssetRegistry {

    /**
     * @return every protected asset, or an empty list when none is configured — in which case no CPA
     *     is computed and the published track simply carries none, rather than a fabricated default
     */
    List<ProtectedAsset> all();
}
