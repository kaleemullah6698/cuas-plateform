package pk.nust.cuas.auth.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import pk.nust.cuas.auth.domain.model.TotpGenerator;

/**
 * Password hashing and TOTP wiring (ARCHITECTURE.md section 7.3).
 *
 * <p>Argon2id with the section 7.3 parameters: 64 MiB of memory, three iterations, parallelism
 * four. The memory cost is the point — it is what makes large-scale offline cracking expensive,
 * because an attacker must find 64 MiB per guess rather than just CPU cycles.
 *
 * <p>The parameters are configurable so tests can lower them. Production must not: a test-speed
 * cost factor in production would make every stored hash cheap to attack, so the defaults here are
 * the real ones and any override is a deliberate, visible act.
 */
@Configuration
public class AuthConfig {

    @Bean
    public PasswordEncoder passwordEncoder(
            @Value("${auth.argon2.salt-length:16}") int saltLength,
            @Value("${auth.argon2.hash-length:32}") int hashLength,
            @Value("${auth.argon2.parallelism:4}") int parallelism,
            @Value("${auth.argon2.memory-kb:65536}") int memoryKb,
            @Value("${auth.argon2.iterations:3}") int iterations) {
        return new Argon2PasswordEncoder(saltLength, hashLength, parallelism, memoryKb, iterations);
    }

    @Bean
    public TotpGenerator totpGenerator() {
        return new TotpGenerator();
    }
}
