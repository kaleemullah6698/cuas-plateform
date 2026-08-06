package pk.nust.cuas.threat.application;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import pk.nust.cuas.threat.domain.policy.ThreatPolicy;
import pk.nust.cuas.threat.domain.port.ThreatPolicyRepository;

/**
 * Holds the policy currently in force, so scoring does not read the database on every track update.
 *
 * <p>The pipeline scores at the track-update rate; the policy changes when somebody publishes a new
 * version. A query per assessment would put a database round-trip on the critical path for data that
 * is almost always identical to last time.
 *
 * <p>The cache is invalidated by {@link #refresh(ThreatPolicy)} when this instance publishes a
 * version. <strong>It is not invalidated by another instance publishing one</strong> — that is what
 * {@code platform.config.policy.updated.v1} exists for, and config-service does not exist until M8.
 * Until then a deployment running several instances must expect a publish to reach the others on
 * their next restart. This is recorded as a deferred item rather than papered over with a polling
 * loop, because a poll interval short enough to be useful would reintroduce the query this cache
 * exists to remove, and one long enough to be cheap would give a false impression of promptness.
 *
 * <p>On first use, when no version has ever been published, section 6.3.4's normative defaults are
 * published and become version one. A threat service that scored nothing until configured would be
 * indistinguishable from a working one watching an empty sky.
 */
@Component
public class ActivePolicySupplier {

    private static final Logger log = LoggerFactory.getLogger(ActivePolicySupplier.class);

    private final ThreatPolicyRepository repository;
    private final Clock clock;
    private final AtomicReference<ThreatPolicy> cached = new AtomicReference<>();

    public ActivePolicySupplier(ThreatPolicyRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /** @return the policy in force, seeding section 6.3.4's defaults on first use */
    public ThreatPolicy active() {
        ThreatPolicy current = cached.get();
        if (current != null) {
            return current;
        }
        ThreatPolicy resolved = repository.active().orElseGet(this::seedDefaults);
        cached.compareAndSet(null, resolved);
        return cached.get();
    }

    /**
     * Replaces the cached policy after this instance published a new version.
     *
     * @param policy the newly published version
     */
    public void refresh(ThreatPolicy policy) {
        cached.set(policy);
        log.info("Active threat policy is now {} version {}", policy.policyId(), policy.version());
    }

    private ThreatPolicy seedDefaults() {
        ThreatPolicy defaults = ThreatPolicy.defaultPolicy(Instant.now(clock));
        log.info("No threat policy published; seeding section 6.3.4 defaults as {}",
                defaults.version());
        return repository.publish(defaults);
    }
}
