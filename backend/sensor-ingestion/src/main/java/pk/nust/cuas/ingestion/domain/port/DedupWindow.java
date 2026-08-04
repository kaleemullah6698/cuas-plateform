package pk.nust.cuas.ingestion.domain.port;

/**
 * A short-lived record of which {@code (sensorId, sensorSeq)} pairs have been seen, used to drop
 * duplicates (ARCHITECTURE.md section 4.1.4: "deduplication by (sensorId, sensorSeq)").
 *
 * <p>The window is bounded in time rather than unbounded: a sensor's sequence numbers are only at
 * risk of a genuine replay for a short interval (a redelivery, an adapter retry), and remembering
 * every sequence number forever would be unbounded state for no benefit. The implementation is
 * Redis-backed (section 4.1.4), so the window is shared across horizontally-scaled instances and a
 * partition rebalance does not reopen it.
 *
 * <p>The check ({@link #seenBefore}) and the mark ({@link #markSeen}) are separate on purpose. A
 * detection is marked seen only <em>after</em> it has been published, so that a publish failure
 * leaves it unmarked and the redelivered record is normalised again rather than dropped — the
 * section 5.6 rule that a detection is never lost outweighs the rare duplicate a mid-publish crash
 * could produce, which the downstream cross-sensor de-duplication (section 4.1.5) absorbs.
 */
public interface DedupWindow {

    /**
     * @param sensorId  the reporting sensor
     * @param sensorSeq the sensor's monotonic sequence number
     * @return {@code true} if this pair has already been marked seen within the window
     */
    boolean seenBefore(String sensorId, long sensorSeq);

    /**
     * Marks a detection as seen for the length of the window. Called after the record is published,
     * so a source duplicate that arrives later is recognised and dropped.
     *
     * @param sensorId  the reporting sensor
     * @param sensorSeq the sensor's monotonic sequence number
     */
    void markSeen(String sensorId, long sensorSeq);
}
