package pk.nust.cuas.simulator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
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
        return new SimulationProperties("http://x", "localhost", 9083, transport, sensors, perSensor, 50, false);
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
