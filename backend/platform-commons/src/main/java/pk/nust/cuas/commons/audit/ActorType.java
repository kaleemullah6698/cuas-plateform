package pk.nust.cuas.commons.audit;

/**
 * Who performed an audited action (ARCHITECTURE.md section 7.5, {@code actor_type} column).
 */
public enum ActorType {

    /** A human operator, identified by user id. */
    USER,

    /** A platform service acting under its own identity. */
    SERVICE,

    /** The platform itself: schedulers, retention jobs, startup and shutdown. */
    SYSTEM
}
