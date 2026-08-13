package pk.nust.cuas.simulator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mockito;
import pk.nust.cuas.detection.v1.DetectionRecord;
import pk.nust.cuas.simulator.client.IngestClient;
import pk.nust.cuas.simulator.client.IngestReport;
import pk.nust.cuas.simulator.config.SimulationProperties;
import pk.nust.cuas.simulator.config.Transport;
import pk.nust.cuas.simulator.generator.SimulatedDetectionFactory;

@ExtendWith(MockitoExtension.class)
class SimulationRunnerTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-04T12:00:00Z"), ZoneOffset.UTC);

    @Mock
    private IngestClient restClient;
    @Mock
    private IngestClient grpcClient;

    private SimulationProperties properties(Transport transport, int sensors, int perSensor) {
        return properties(transport, sensors, perSensor, "");
    }

    private SimulationProperties properties(Transport transport, int sensors, int perSensor,
                                            String runLabel) {
        return new SimulationProperties("http://x", "localhost", 9083, transport, sensors, perSensor,
                50, false, runLabel, false, Duration.ofSeconds(5));
    }

    private SimulationRunner runner(SimulationProperties properties, IngestClient... clients) {
        return new SimulationRunner(new SimulatedDetectionFactory(), List.of(clients), properties, CLOCK);
    }

    @Test
    void generatesSensorsTimesDetectionsAndDispatchesThemAll() {
        when(restClient.transport()).thenReturn(Transport.REST);
        when(restClient.send(anyList())).thenReturn(IngestReport.empty(Transport.REST));

        runner(properties(Transport.REST, 2, 3), restClient).execute();

        ArgumentCaptor<List<DetectionRecord>> sent = ArgumentCaptor.forClass(List.class);
        verify(restClient).send(sent.capture());
        assertThat(sent.getValue()).hasSize(6); // 2 sensors × 3 detections
        assertThat(sent.getValue()).allSatisfy(d -> assertThat(d.getSensorSeq()).isBetween(1L, 3L));
        // Two distinct sensors, each emitting three sequence numbers.
        assertThat(sent.getValue().stream().map(DetectionRecord::getSensorId).distinct().count())
                .isEqualTo(2L);
    }

    /**
     * The intake deduplicates on {@code (sensorId, sensorSeq)} so at-least-once redelivery is safe
     * (ARCHITECTURE.md section 4.1.4). With fixed sensor ids and sequences restarting at 1, a second
     * replay is indistinguishable from a redelivery of the first and is dropped in full at
     * normalisation — every detection reported as accepted, no track produced, and nothing logged
     * above DEBUG to say why. The run label is what makes two runs distinguishable.
     */
    @Test
    void runsWithDifferentLabelsEmitDisjointSensorIds() {
        when(restClient.transport()).thenReturn(Transport.REST);
        when(restClient.send(anyList())).thenReturn(IngestReport.empty(Transport.REST));

        runner(properties(Transport.REST, 2, 3, "runA"), restClient).execute();
        runner(properties(Transport.REST, 2, 3, "runB"), restClient).execute();

        ArgumentCaptor<List<DetectionRecord>> sent = ArgumentCaptor.forClass(List.class);
        verify(restClient, Mockito.times(2)).send(sent.capture());

        Set<String> first = sent.getAllValues().get(0).stream()
                .map(DetectionRecord::getSensorId).collect(Collectors.toSet());
        Set<String> second = sent.getAllValues().get(1).stream()
                .map(DetectionRecord::getSensorId).collect(Collectors.toSet());

        assertThat(first).allSatisfy(id -> assertThat(id).endsWith("-runA"));
        assertThat(second).allSatisfy(id -> assertThat(id).endsWith("-runB"));
        assertThat(first).doesNotContainAnyElementsOf(second);
    }

    /** An unset label reproduces the original ids exactly, so nothing existing changes shape. */
    @Test
    void anEmptyRunLabelLeavesSensorIdsUnchanged() {
        when(restClient.transport()).thenReturn(Transport.REST);
        when(restClient.send(anyList())).thenReturn(IngestReport.empty(Transport.REST));

        runner(properties(Transport.REST, 1, 1, ""), restClient).execute();

        ArgumentCaptor<List<DetectionRecord>> sent = ArgumentCaptor.forClass(List.class);
        verify(restClient).send(sent.capture());
        assertThat(sent.getValue().get(0).getSensorId()).isEqualTo("sim-rf-0");
    }

    /**
     * The property that makes continuous emission work at all. sensor-ingestion deduplicates on
     * {@code (sensorId, sensorSeq)} (§4.1.4), so repeating a cycle with the ids fixed and the
     * sequence restarting at 1 would be a redelivery and would be discarded in full — which is
     * exactly what happened to every replay after the first before the run label existed. A rising
     * sequence keeps the ids stable *and* stays clear of the dedup window, which is what a real
     * sensor does.
     */
    @Test
    void consecutiveCyclesKeepSensorIdsAndAdvanceSequenceNumbers() {
        when(restClient.transport()).thenReturn(Transport.REST);
        when(restClient.send(anyList())).thenReturn(IngestReport.empty(Transport.REST));

        SimulationRunner runner = runner(properties(Transport.REST, 2, 3), restClient);
        runner.execute();
        runner.execute();

        ArgumentCaptor<List<DetectionRecord>> sent = ArgumentCaptor.forClass(List.class);
        verify(restClient, Mockito.times(2)).send(sent.capture());

        List<DetectionRecord> first = sent.getAllValues().get(0);
        List<DetectionRecord> second = sent.getAllValues().get(1);

        // Same sensors both cycles — a sensor that renamed itself every few seconds would never be
        // reported ONLINE, because health is keyed on the id.
        assertThat(second.stream().map(DetectionRecord::getSensorId).collect(Collectors.toSet()))
                .isEqualTo(first.stream().map(DetectionRecord::getSensorId).collect(Collectors.toSet()));

        // First cycle 1..3, second cycle 4..6 — disjoint, so nothing is dropped as a duplicate.
        assertThat(first.stream().map(DetectionRecord::getSensorSeq).distinct().sorted().toList())
                .containsExactly(1L, 2L, 3L);
        assertThat(second.stream().map(DetectionRecord::getSensorSeq).distinct().sorted().toList())
                .containsExactly(4L, 5L, 6L);
    }

    /**
     * sensor-ingestion decides a sensor is ONLINE from how recently it last saw it
     * ({@code health.online-within}, 30 s). Detections stamped further back than that arrive already
     * stale, and the console reports every sensor OFFLINE however recently the simulator ran — which
     * is exactly what it did, because the batch was offset by {@code perSensor} *seconds* while being
     * spaced 100 ms apart, leaving the freshest detection ~90 s old at the default size.
     */
    @Test
    void theNewestDetectionInACycleCarriesTheCurrentInstant() {
        when(restClient.transport()).thenReturn(Transport.REST);
        when(restClient.send(anyList())).thenReturn(IngestReport.empty(Transport.REST));

        runner(properties(Transport.REST, 1, 100), restClient).execute();

        ArgumentCaptor<List<DetectionRecord>> sent = ArgumentCaptor.forClass(List.class);
        verify(restClient).send(sent.capture());

        long newestEpochMs = sent.getValue().stream()
                .mapToLong(d -> d.getSensorTime().getSeconds() * 1000L
                        + d.getSensorTime().getNanos() / 1_000_000L)
                .max()
                .orElseThrow();
        assertThat(newestEpochMs).isEqualTo(CLOCK.instant().toEpochMilli());
    }

    /** A one-shot run must be unchanged by the offset: sequences still start at 1. */
    @Test
    void aSingleRunStillStartsItsSequenceAtOne() {
        when(restClient.transport()).thenReturn(Transport.REST);
        when(restClient.send(anyList())).thenReturn(IngestReport.empty(Transport.REST));

        runner(properties(Transport.REST, 1, 3), restClient).execute();

        ArgumentCaptor<List<DetectionRecord>> sent = ArgumentCaptor.forClass(List.class);
        verify(restClient).send(sent.capture());
        assertThat(sent.getValue().stream().map(DetectionRecord::getSensorSeq).sorted().toList())
                .containsExactly(1L, 2L, 3L);
    }

    @Test
    void driftsOnlyTheSelectedTransport() {
        when(restClient.transport()).thenReturn(Transport.REST);
        when(grpcClient.transport()).thenReturn(Transport.GRPC);
        when(restClient.send(anyList())).thenReturn(IngestReport.empty(Transport.REST));

        runner(properties(Transport.REST, 1, 2), restClient, grpcClient).execute();

        verify(restClient).send(anyList());
        verify(grpcClient, never()).send(anyList());
    }

    @Test
    void bothTransportsAreDrivenWhenTransportIsBoth() {
        // With BOTH, the runner selects every client without inspecting transport(), so only send()
        // is exercised here.
        when(restClient.send(anyList())).thenReturn(IngestReport.empty(Transport.REST));
        when(grpcClient.send(anyList())).thenReturn(IngestReport.empty(Transport.GRPC));

        runner(properties(Transport.BOTH, 1, 2), restClient, grpcClient).execute();

        verify(restClient).send(anyList());
        verify(grpcClient).send(anyList());
    }

    @Test
    void doesNotRunOnStartupUnlessConfigured() {
        SimulationRunner runner = runner(properties(Transport.REST, 1, 1), restClient);

        runner.run(new org.springframework.boot.DefaultApplicationArguments());

        verify(restClient, never()).send(any());
    }

    // Guards against accidentally driving nothing when the transport has no client.
    @Test
    void failsFastWhenNoClientMatchesTheTransport() {
        when(grpcClient.transport()).thenReturn(Transport.GRPC);
        SimulationRunner runner = runner(properties(Transport.REST, 1, 1), grpcClient);

        org.assertj.core.api.Assertions.assertThatThrownBy(runner::execute)
                .isInstanceOf(IllegalStateException.class);
        Mockito.verifyNoInteractions(restClient);
    }
}
