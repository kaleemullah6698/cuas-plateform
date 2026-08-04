package pk.nust.cuas.simulator.config;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import pk.nust.cuas.simulator.client.GrpcIngestClient;
import pk.nust.cuas.simulator.client.RestIngestClient;

/**
 * Wires the simulator's clients against the real sensor-ingestion endpoints.
 *
 * <p>The REST client is bound to the intake's base URL and the gRPC channel to its host and port —
 * the ingestion service's own ports, not the gateway, because M4 does not route or rate-limit the
 * intake yet. Both clients are always created; the runner selects which to use from
 * {@code simulator.transport}. The channel connects lazily, so creating it when only REST is used
 * costs nothing.
 */
@Configuration
@EnableConfigurationProperties(SimulationProperties.class)
public class SimulationConfig {

    @Bean
    public RestClient ingestRestClient(SimulationProperties properties) {
        return RestClient.builder().baseUrl(properties.restBaseUrl()).build();
    }

    @Bean(destroyMethod = "shutdownNow")
    public ManagedChannel ingestChannel(SimulationProperties properties) {
        return ManagedChannelBuilder.forAddress(properties.grpcHost(), properties.grpcPort())
                .usePlaintext()
                .build();
    }

    @Bean
    public RestIngestClient restIngestClient(RestClient ingestRestClient, SimulationProperties properties) {
        return new RestIngestClient(ingestRestClient, properties.batchSize());
    }

    @Bean
    public GrpcIngestClient grpcIngestClient(ManagedChannel ingestChannel) {
        return new GrpcIngestClient(ingestChannel);
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
