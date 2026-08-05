package pk.nust.cuas.orchestrator.config;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import pk.nust.cuas.orchestrator.application.ProviderSelector;
import pk.nust.cuas.orchestrator.application.port.InferenceCache;
import pk.nust.cuas.orchestrator.application.port.InferenceProvider;
import pk.nust.cuas.orchestrator.domain.model.AiServiceDescriptor;
import pk.nust.cuas.orchestrator.domain.service.AiServiceRegistry;
import pk.nust.cuas.orchestrator.infrastructure.cache.NoInferenceCache;
import pk.nust.cuas.orchestrator.infrastructure.cache.RedisInferenceCache;
import pk.nust.cuas.orchestrator.infrastructure.provider.GrpcInferenceProvider;

/**
 * Wires provider selection and the inference cache (ARCHITECTURE.md sections 4.1.6, 6.4).
 */
@Configuration
@EnableConfigurationProperties(CacheProperties.class)
public class OrchestrationConfig {

    private static final Logger log = LoggerFactory.getLogger(OrchestrationConfig.class);

    /**
     * The gRPC client for real AI services (section 6.2).
     *
     * <p>Unconditional, like the mock: one instance serves every {@code mode: grpc} service, with
     * channels created per endpoint on demand. A bean per model would be the code change §6.1
     * forbids — and would mean a newly trained model could not be added without a redeploy, which is
     * the opposite of what §6.4's config switch promises.
     *
     * <p>{@code destroyMethod} closes the cached channels on shutdown so connections are not leaked.
     */
    @Bean(destroyMethod = "close")
    public GrpcInferenceProvider grpcInferenceProvider(
            @org.springframework.beans.factory.annotation.Value("${ai.grpc.plaintext:true}")
            boolean plaintext) {
        return new GrpcInferenceProvider(plaintext);
    }

    /**
     * Binds every available provider by mode, then checks that each <em>configured</em> service has
     * one.
     *
     * <p>Validated against the registry rather than against every mode that exists, so a deployment
     * running only mocks needs no gRPC client present. A service configured for a mode nothing
     * implements fails the context here rather than on its first inference — the same
     * fail-at-startup rule the registry itself follows.
     */
    @Bean
    public ProviderSelector providerSelector(List<InferenceProvider> providers,
                                             AiServiceRegistry registry) {
        ProviderSelector selector = new ProviderSelector(providers);
        for (AiServiceDescriptor service : registry.all()) {
            if (!selector.supports(service.mode())) {
                throw new IllegalStateException("service '" + service.id() + "' is configured mode="
                        + service.mode() + ", but no provider implements that mode");
            }
        }
        log.info("Provider selection ready for {} service(s) across {} provider(s)",
                registry.all().size(), providers.size());
        return selector;
    }

    /**
     * The inference cache (section 4.1.6, TTL 60 s).
     *
     * <p>Redis when enabled, a permanent-miss store otherwise. As with the track hot state, the
     * no-op is a supported configuration rather than a test double: the port is written so a miss is
     * always correct, and that claim is only credible if something exercises it.
     */
    @Bean
    @ConditionalOnProperty(name = "ai.cache.enabled", havingValue = "true", matchIfMissing = true)
    public InferenceCache redisInferenceCache(StringRedisTemplate redis, CacheProperties properties) {
        return new RedisInferenceCache(redis, properties.ttl());
    }

    @Bean
    @ConditionalOnProperty(name = "ai.cache.enabled", havingValue = "false")
    public InferenceCache noInferenceCache() {
        log.info("Inference cache disabled; every call reaches a provider");
        return new NoInferenceCache();
    }
}
