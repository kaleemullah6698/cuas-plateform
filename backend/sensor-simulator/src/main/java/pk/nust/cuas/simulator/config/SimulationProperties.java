package pk.nust.cuas.simulator.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * How a simulation run is shaped, bound from the {@code simulator.*} namespace.
 *
 * @param restBaseUrl         base URL of the sensor-ingestion REST intake (its own port, not the gateway)
 * @param grpcHost            host of the sensor-ingestion gRPC intake
 * @param grpcPort            port of the sensor-ingestion gRPC intake
 * @param transport           which intake(s) to drive
 * @param sensors             how many distinct simulated sensors to emit from
 * @param detectionsPerSensor how many detections each sensor emits
 * @param batchSize           how many detections per REST request
 * @param runOnStartup        whether a run fires automatically on boot; off by default so importing
 *                            the module or running its tests does not generate traffic
 * @param runLabel            distinguishes one run's sensors from another's. Empty by default, which
 *                            reproduces the original fixed ids.
 *                            <p>Without it a second replay is silently discarded in full.
 *                            sensor-ingestion deduplicates on {@code (sensorId, sensorSeq)} so that
 *                            at-least-once redelivery is safe (section 4.1.4), and every run emitted
 *                            the same three sensor ids with sequences restarting at 1 — so from the
 *                            intake's point of view the second run <em>was</em> a redelivery of the
 *                            first. Every detection was accepted to the raw topic and then dropped at
 *                            normalisation, producing no tracks and no error. The dedup window is
 *                            right; it was the simulator that was not distinguishable between runs.
 * @param continuous          emit indefinitely instead of once. Off by default, so every existing
 *                            invocation behaves exactly as before.
 *                            <p>A single run is a burst: 300 detections in about four seconds, after
 *                            which the process exits. track-correlation drops a track
 *                            {@code lost-timeout} (30 s) after its last detection and
 *                            sensor-ingestion reports a sensor OFFLINE outside {@code online-within}
 *                            (30 s), so roughly forty-five seconds later the live view is correctly
 *                            empty and an operator console shows zeros. That is the pipeline working,
 *                            not failing — but it cannot demonstrate a running airspace.
 *                            <p>In continuous mode the sensor ids stay fixed and the sequence numbers
 *                            keep climbing across cycles, which is what a real sensor does. No run
 *                            label is needed, because a rising sequence never collides with the
 *                            intake's {@code (sensorId, sensorSeq)} dedup window.
 * @param interval            pause between cycles in continuous mode. The default is deliberately
 *                            below track-correlation's 10 s coast-timeout, so tracks stay live rather
 *                            than flickering between CONFIRMED and COASTING.
 */
@ConfigurationProperties(prefix = "simulator")
public record SimulationProperties(
        @DefaultValue("http://localhost:8083") String restBaseUrl,
        @DefaultValue("localhost") String grpcHost,
        @DefaultValue("9083") int grpcPort,
        @DefaultValue("REST") Transport transport,
        @DefaultValue("3") int sensors,
        @DefaultValue("100") int detectionsPerSensor,
        @DefaultValue("50") int batchSize,
        @DefaultValue("false") boolean runOnStartup,
        @DefaultValue("") String runLabel,
        @DefaultValue("false") boolean continuous,
        @DefaultValue("PT5S") Duration interval) {

    public SimulationProperties {
        if (sensors < 1) {
            throw new IllegalArgumentException("simulator.sensors must be at least 1");
        }
        if (detectionsPerSensor < 1) {
            throw new IllegalArgumentException("simulator.detections-per-sensor must be at least 1");
        }
        if (batchSize < 1) {
            throw new IllegalArgumentException("simulator.batch-size must be at least 1");
        }
    }
}
