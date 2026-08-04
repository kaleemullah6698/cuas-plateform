package pk.nust.cuas.simulator;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import pk.nust.cuas.detection.v1.DetectionRecord;
import pk.nust.cuas.detection.v1.SensorModality;
import pk.nust.cuas.simulator.client.IngestClient;
import pk.nust.cuas.simulator.client.IngestReport;
import pk.nust.cuas.simulator.config.SimulationProperties;
import pk.nust.cuas.simulator.config.Transport;
import pk.nust.cuas.simulator.generator.SimulatedDetectionFactory;

/**
 * Orchestrates a simulation run: generate the configured detections and drive them through the
 * selected real intake(s).
 *
 * <p>A run fires on startup only when {@code simulator.run-on-startup} is set, so the module can be
 * imported, wired, and tested without generating traffic. {@link #execute()} is public so a test or
 * an operator can trigger a run directly and inspect the per-transport reports.
 *
 * <p>Sensors are assigned rotating modalities and their detections carry increasing sequence numbers
 * and timestamps, so a run produces a realistic multi-sensor, multi-modality stream that the
 * ingestion pipeline normalises, de-duplicates, and reports health for exactly as it would live
 * traffic.
 */
@Component
public class SimulationRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SimulationRunner.class);
    private static final SensorModality[] MODALITY_ROTATION = {
            SensorModality.MODALITY_RF, SensorModality.MODALITY_RADAR, SensorModality.MODALITY_EO_IR};

    private final SimulatedDetectionFactory factory;
    private final List<IngestClient> clients;
    private final SimulationProperties properties;
    private final Clock clock;

    public SimulationRunner(SimulatedDetectionFactory factory,
                            List<IngestClient> clients,
                            SimulationProperties properties,
                            Clock clock) {
        this.factory = factory;
        this.clients = clients;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (properties.runOnStartup()) {
            execute();
        }
    }

    /**
     * Generates the configured detections and sends them through each selected intake.
     *
     * @return one report per intake driven
     */
    public List<IngestReport> execute() {
        List<DetectionRecord> detections = generate();
        List<IngestClient> selected = selectedClients();
        log.info("Simulating {} detections from {} sensor(s) over {} transport(s)",
                detections.size(), properties.sensors(), selected.size());

        List<IngestReport> reports = new ArrayList<>();
        for (IngestClient client : selected) {
            reports.add(client.send(detections));
        }
        reports.forEach(r -> log.info("Transport {}: sent {}, accepted {}, rejected {}",
                r.transport(), r.sent(), r.accepted(), r.rejected()));
        return reports;
    }

    private List<IngestClient> selectedClients() {
        List<IngestClient> selected = clients.stream()
                .filter(client -> properties.transport() == Transport.BOTH
                        || client.transport() == properties.transport())
                .toList();
        if (selected.isEmpty()) {
            throw new IllegalStateException("No ingest client for transport " + properties.transport());
        }
        return selected;
    }

    private List<DetectionRecord> generate() {
        Instant base = clock.instant().minusSeconds(properties.detectionsPerSensor());
        List<DetectionRecord> detections = new ArrayList<>();
        for (int sensor = 0; sensor < properties.sensors(); sensor++) {
            SensorModality modality = MODALITY_ROTATION[sensor % MODALITY_ROTATION.length];
            String sensorId = "sim-" + label(modality) + '-' + sensor;
            for (long seq = 1; seq <= properties.detectionsPerSensor(); seq++) {
                detections.add(factory.create(sensorId, modality, seq, base.plusMillis(seq * 100)));
            }
        }
        return detections;
    }

    private String label(SensorModality modality) {
        return switch (modality) {
            case MODALITY_RADAR -> "radar";
            case MODALITY_EO_IR -> "eo-ir";
            default -> "rf";
        };
    }
}
