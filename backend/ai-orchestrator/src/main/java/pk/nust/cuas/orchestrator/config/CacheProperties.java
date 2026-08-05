package pk.nust.cuas.orchestrator.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * The inference cache's settings (ARCHITECTURE.md section 4.1.6).
 *
 * @param enabled whether to cache at all; false runs against a permanent-miss store, which is a
 *                supported configuration rather than a degraded one
 * @param ttl     how long a result stays valid. Section 4.1.6 specifies 60 s — long enough that a
 *                burst of identical detections costs one GPU call, short enough that a stale
 *                classification cannot outlive the situation that produced it.
 */
@ConfigurationProperties(prefix = "ai.cache")
public record CacheProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("PT60S") Duration ttl) {
}
