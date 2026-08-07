package pk.nust.cuas.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Config-service (ARCHITECTURE.md §4.1.12): runtime configuration without redeploy — namespaced
 * values with a full version history and before/after change auditing, rollback, and change
 * propagation over the gRPC WatchConfig stream and the compact platform.config.* topics. Owns
 * configdb; consumes nothing.
 */
@SpringBootApplication
public class ConfigServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConfigServiceApplication.class, args);
    }
}
