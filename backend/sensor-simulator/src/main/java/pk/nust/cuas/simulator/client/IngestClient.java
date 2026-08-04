package pk.nust.cuas.simulator.client;

import java.util.List;
import pk.nust.cuas.detection.v1.DetectionRecord;
import pk.nust.cuas.simulator.config.Transport;

/**
 * Sends detections to a real sensor-ingestion intake.
 *
 * <p>The abstraction the runner talks to; the REST and gRPC implementations differ only in which
 * real endpoint they call. Both speak the actual wire contract, so a simulation exercises the same
 * path a real sensor adapter would.
 */
public interface IngestClient {

    /** @return which intake this client drives */
    Transport transport();

    /**
     * @param detections the detections to send
     * @return how the intake accounted for them
     */
    IngestReport send(List<DetectionRecord> detections);
}
