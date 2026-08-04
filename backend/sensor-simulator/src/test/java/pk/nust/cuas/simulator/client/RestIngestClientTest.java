package pk.nust.cuas.simulator.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import pk.nust.cuas.detection.v1.DetectionRecord;
import pk.nust.cuas.detection.v1.SensorModality;
import pk.nust.cuas.simulator.config.Transport;
import pk.nust.cuas.simulator.generator.SimulatedDetectionFactory;

/**
 * The REST client against a mock HTTP server: it must POST the canonical detection JSON to the real
 * endpoint path, in the configured batch size, and total the counts the endpoint returns.
 */
class RestIngestClientTest {

    private final SimulatedDetectionFactory factory = new SimulatedDetectionFactory();

    private List<DetectionRecord> detections(int count) {
        List<DetectionRecord> list = new ArrayList<>();
        for (long seq = 1; seq <= count; seq++) {
            list.add(factory.create("sim-rf-0", SensorModality.MODALITY_RF, seq, Instant.parse("2026-08-04T12:00:00Z")));
        }
        return list;
    }

    @Test
    void postsCanonicalJsonToTheIngestEndpointInBatches() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://ingestion:8083");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        // Four detections at batch size two => two POSTs; each accepts both.
        server.expect(ExpectedCount.times(2), requestTo("http://ingestion:8083/ingest/detections"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("\"sensorId\":\"sim-rf-0\"")))
                .andRespond(withSuccess("{\"accepted\":2,\"rejected\":0}", MediaType.APPLICATION_JSON));

        RestIngestClient client = new RestIngestClient(builder.build(), 2);
        IngestReport report = client.send(detections(4));

        server.verify();
        assertThat(report.transport()).isEqualTo(Transport.REST);
        assertThat(report.sent()).isEqualTo(4);
        assertThat(report.accepted()).isEqualTo(4);
        assertThat(report.rejected()).isZero();
    }

    @Test
    void sumsRejectionsReportedByTheEndpoint() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://ingestion:8083");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://ingestion:8083/ingest/detections"))
                .andRespond(withSuccess("{\"accepted\":1,\"rejected\":1}", MediaType.APPLICATION_JSON));

        RestIngestClient client = new RestIngestClient(builder.build(), 50);
        IngestReport report = client.send(detections(2));

        assertThat(report.accepted()).isEqualTo(1);
        assertThat(report.rejected()).isEqualTo(1);
    }
}
