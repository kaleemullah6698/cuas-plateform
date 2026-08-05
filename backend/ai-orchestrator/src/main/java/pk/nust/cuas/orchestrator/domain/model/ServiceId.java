package pk.nust.cuas.orchestrator.domain.model;

/**
 * The identity of a registered AI service (ARCHITECTURE.md section 6.1).
 *
 * <p>An opaque string that comes from configuration and is never enumerated in code. The orchestrator
 * knows that services exist and which modalities they accept; it never knows what any of them is
 * called. <b>If a service name ever appears as a constant in Track A, this type has failed at its
 * job</b> — section 6.1's guarantee that a fifth model is a configuration entry holds only while
 * service identity stays data.
 *
 * <p>{@code ModelAgnosticismTest} enforces that by scanning this module's source for the names in
 * section 6.3, comments included. Naming one even in prose is the first step towards branching on
 * it, so the check is deliberately blunt rather than comment-aware.
 *
 * @param value the configured service id, e.g. the key under {@code ai.services}
 */
public record ServiceId(String value) {

    public ServiceId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("a service id is required");
        }
        value = value.trim();
    }

    @Override
    public String toString() {
        return value;
    }
}
