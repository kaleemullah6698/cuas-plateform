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

    /**
     * Where the next cycle's sequence numbers start.
     *
     * <p>Zero for a one-shot run, which reproduces the original behaviour exactly: sequences 1..N.
     * In continuous mode it advances by {@code detectionsPerSensor} each cycle, so the ids stay fixed
     * while the sequence keeps climbing — a real sensor, rather than the same burst replayed. That is
     * also what keeps the intake's {@code (sensorId, sensorSeq)} dedup window from discarding every
     * cycle after the first, with no run label needed.
     */
    private long sequenceOffset;

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.runOnStartup()) {
            return;
        }
        if (properties.continuous()) {
            executeContinuously();
        } else {
            execute();
        }
    }

    /**
     * Emits a cycle every {@code simulator.interval} until the process is stopped.
     *
     * <p>Development only, and the reason it exists is specific: a single run is a burst, and roughly
     * forty-five seconds later track-correlation has dropped every track past its 30 s lost-timeout
     * and sensor-ingestion reports every sensor OFFLINE past its 30 s online-within. An operator
     * console then correctly shows zeros. Continuous emission is what makes a live airspace live.
     *
     * <p>Interruption is honoured rather than swallowed: `docker compose stop` should stop it.
     */
    public void executeContinuously() {
        log.info("Continuous simulation: {} detection(s) per sensor from {} sensor(s) every {}",
                properties.detectionsPerSensor(), properties.sensors(), properties.interval());
        while (!Thread.currentThread().isInterrupted()) {
            try {
                execute();
            } catch (RuntimeException ex) {
                // A cycle must not be able to end the run. The intake answers 503
                // ("Could not publish to sensor.detection.raw.v1") whenever Kafka is briefly
                // unavailable — a broker restart, a rebalance — and an emitter that died on the
                // first one would leave an operator staring at a console that silently stopped
                // updating. A real sensor adapter keeps reporting; so does this one.
                log.warn("Simulation cycle failed ({}); continuing", ex.getMessage());
            }
            try {
                Thread.sleep(properties.interval().toMillis());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                log.info("Continuous simulation interrupted; stopping");
                return;
            }
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
        int perSensor = properties.detectionsPerSensor();

        // The cycle ends at "now": detections are spaced 100 ms apart, so the window is
        // perSensor * 100 ms wide and the newest detection carries the current instant.
        //
        // This previously subtracted perSensor *seconds* while spacing the detections 100 ms apart,
        // which stamped the whole batch far in the past — with the default 100 detections the
        // freshest one was about 90 seconds old. sensor-ingestion decides ONLINE from
        // `health.online-within` (30 s), so every sensor was reported OFFLINE the moment it was
        // ingested, no matter how recently the simulator had run. That is why the console showed
        // sensors online 0/n even straight after a replay.
        Instant base = clock.instant().minusMillis((long) perSensor * 100L);
        long offset = sequenceOffset;

        List<DetectionRecord> detections = new ArrayList<>();
        for (int sensor = 0; sensor < properties.sensors(); sensor++) {
            SensorModality modality = MODALITY_ROTATION[sensor % MODALITY_ROTATION.length];
            String sensorId = "sim-" + label(modality) + '-' + sensor + runSuffix();
            for (long seq = 1; seq <= perSensor; seq++) {
                detections.add(factory.create(sensorId, modality, offset + seq,
                        base.plusMillis(seq * 100)));
            }
        }

        // Advanced after generating, so a one-shot run emits 1..N exactly as it always did.
        sequenceOffset = offset + perSensor;
        return detections;
    }

    /**
     * Appended to every sensor id so one run's sensors are distinguishable from another's.
     *
     * <p>Empty unless {@code simulator.run-label} is set, which reproduces the original fixed ids
     * exactly. It exists because sensor-ingestion deduplicates on {@code (sensorId, sensorSeq)} to
     * make at-least-once redelivery safe (section 4.1.4): with fixed ids and sequences restarting at
     * 1 every run, a second replay is indistinguishable from a redelivery of the first and is
     * discarded in full, silently and with every detection still reported as accepted.
     */
    private String runSuffix() {
        String label = properties.runLabel();
        return label.isBlank() ? "" : "-" + label;
    }

    private String label(SensorModality modality) {
        return switch (modality) {
            case MODALITY_RADAR -> "radar";
            case MODALITY_EO_IR -> "eo-ir";
            default -> "rf";
        };
    }
}
