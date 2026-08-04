package pk.nust.cuas.simulator.config;

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
        @DefaultValue("false") boolean runOnStartup) {

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
