package pk.nust.cuas.config.domain.model;

/**
 * The configuration namespace families (ARCHITECTURE.md §4.1.12). Domain mirror of the
 * {@code ConfigNamespace} contract enum, kept here so the domain never imports a generated type.
 *
 * <p>The kind is a fact about what a namespace configures, and it decides which config topic a change
 * is announced on and which §7.4 permission may change it — a policy retune is not the same authority
 * as registering a sensor.
 */
public enum ConfigNamespaceKind {
    SENSOR,
    POLICY,
    AI,
    FEATURE_FLAG,
    RETENTION,
    PROTECTED_ASSET,
    GENERAL
}
