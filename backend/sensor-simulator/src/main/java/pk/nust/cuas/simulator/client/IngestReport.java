package pk.nust.cuas.simulator.client;

import pk.nust.cuas.simulator.config.Transport;

/**
 * The outcome of sending a set of detections to one intake: how many were sent, and how the intake
 * accounted for them.
 *
 * @param transport which intake was driven
 * @param sent      detections dispatched
 * @param accepted  detections the intake accepted
 * @param rejected  detections the intake rejected
 */
public record IngestReport(Transport transport, int sent, int accepted, int rejected) {

    public static IngestReport empty(Transport transport) {
        return new IngestReport(transport, 0, 0, 0);
    }

    public IngestReport plus(int sent, int accepted, int rejected) {
        return new IngestReport(transport, this.sent + sent, this.accepted + accepted,
                this.rejected + rejected);
    }
}
