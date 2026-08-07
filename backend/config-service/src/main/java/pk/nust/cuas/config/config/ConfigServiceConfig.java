package pk.nust.cuas.config.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the small set of shared beans config-service needs. */
@Configuration
public class ConfigServiceConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
