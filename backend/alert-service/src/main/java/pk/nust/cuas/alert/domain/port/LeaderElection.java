package pk.nust.cuas.alert.domain.port;

/**
 * Which instance runs the escalation sweep (ARCHITECTURE.md section 4.1.8, "with leader election").
 *
 * <p>The sweep must normally run on one instance at a time. Without that, every replica would fire
 * the same escalation and the operator would be paged once per replica for one unacknowledged
 * alert — a duplicate-notification failure with the same cause and the same consequence as the
 * duplicate alerts section 5.7 warns about.
 *
 * <p><strong>Leadership is a lease, not a title, and the sweep does not depend on it for
 * correctness.</strong> {@link #isLeader()} answers for now, and the answer can change between two
 * calls; under a network partition two instances can briefly both believe they lead, and no lease
 * protocol over a single Redis prevents that. The sweep is therefore written to be safe when it
 * happens: each escalation re-reads the alert and applies its transition under the aggregate's own
 * state guard, so the loser of the race changes nothing and publishes nothing. Leader election
 * removes the contention; the aggregate is what removes the duplicate.
 */
public interface LeaderElection {

    /**
     * @return {@code true} when this instance currently holds the lease. Acquires or renews it as a
     *         side effect, so an instance that stops asking loses leadership when the lease expires
     *         — which is what makes a crashed leader recoverable with nobody intervening.
     */
    boolean isLeader();

    /** Releases the lease, if held. Called on shutdown so a rolling restart does not wait it out. */
    void release();
}
