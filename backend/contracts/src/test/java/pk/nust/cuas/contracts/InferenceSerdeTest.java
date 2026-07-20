package pk.nust.cuas.contracts;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Timestamp;
import org.junit.jupiter.api.Test;
import pk.nust.cuas.detection.v1.DetectionRecord;
import pk.nust.cuas.detection.v1.SensorModality;
import pk.nust.cuas.inference.v1.BatchInferenceRequest;
import pk.nust.cuas.inference.v1.BatchInferenceResponse;
import pk.nust.cuas.inference.v1.ComputeDevice;
import pk.nust.cuas.inference.v1.ConfidenceReport;
import pk.nust.cuas.inference.v1.FeatureAttribution;
import pk.nust.cuas.inference.v1.HealthRequest;
import pk.nust.cuas.inference.v1.HealthResponse;
import pk.nust.cuas.inference.v1.InferenceOptions;
import pk.nust.cuas.inference.v1.InferenceRequest;
import pk.nust.cuas.inference.v1.InferenceResponse;
import pk.nust.cuas.inference.v1.InferenceStatus;
import pk.nust.cuas.inference.v1.ModelInfo;
import pk.nust.cuas.inference.v1.ModelInfoRequest;
import pk.nust.cuas.inference.v1.Prediction;

/**
 * Serialization round-trip coverage for every message in {@code inference.proto}
 * (ARCHITECTURE.md section 6.2), including the cross-package embedding of a detection
 * {@link DetectionRecord} inside an {@link InferenceRequest}.
 */
class InferenceSerdeTest {

    @Test
    void inferenceRequestWithEmbeddedDetectionRoundTrips() throws Exception {
        InferenceRequest original = InferenceRequest.newBuilder()
                .setRequestId("01J8X4K3N2R5S8")
                .setTrackId("TRK-0473")
                .setCorrelationId("corr-01")
                .setRequestedAt(Timestamp.newBuilder().setSeconds(1_784_562_300).build())
                .addDetections(DetectionRecord.newBuilder()
                        .setDetectionId("det-1").setSensorId("RF-01")
                        .setModality(SensorModality.MODALITY_RF).build())
                .setOptions(InferenceOptions.newBuilder()
                        .setModelVersion("1.3.0").setTimeoutMs(100).setMinConfidence(0.5f)
                        .setIncludeFeatures(true).setExplain(true).build())
                .build();

        InferenceRequest parsed = InferenceRequest.parseFrom(original.toByteArray());

        assertThat(parsed).isEqualTo(original);
        assertThat(parsed.getDetectionsCount()).isEqualTo(1);
        assertThat(parsed.getDetections(0).getSensorId()).isEqualTo("RF-01");
        assertThat(parsed.getOptions().getModelVersion()).isEqualTo("1.3.0");
    }

    @Test
    void inferenceResponseRoundTripsWithAllFields() throws Exception {
        InferenceResponse original = InferenceResponse.newBuilder()
                .setRequestId("01J8X4K3N2R5S8")
                .setTrackId("TRK-0473")
                .setStatus(InferenceStatus.STATUS_OK)
                .addPredictions(Prediction.newBuilder()
                        .setLabel("DJI_Mavic_3").setLabelClass("DRONE")
                        .setConfidence(0.91f).setRank(1)
                        .putAttributes("range_m", 1200.0f).build())
                .setConfidence(ConfidenceReport.newBuilder()
                        .setScore(0.91f).setCalibratedScore(0.88f).setEntropy(0.12f)
                        .setMethod("temperature_scaled").setIsReliable(true).build())
                .setModelId("rf-classifier").setModelVersion("1.3.0").setModelSha("4a7fc21e")
                .setInferenceMs(43).setQueueWaitMs(5).setDevice(ComputeDevice.DEVICE_CUDA)
                .addAttributions(FeatureAttribution.newBuilder()
                        .setFeature("center_frequency_hz").setContribution(0.34f).build())
                .setEmbedding(ByteString.copyFrom(new byte[]{9, 8, 7}))
                .setErrorMessage("")
                .build();

        InferenceResponse parsed = InferenceResponse.parseFrom(original.toByteArray());

        assertThat(parsed).isEqualTo(original);
        assertThat(parsed.getPredictions(0).getAttributesMap()).containsEntry("range_m", 1200.0f);
        assertThat(parsed.getDevice()).isEqualTo(ComputeDevice.DEVICE_CUDA);
        assertThat(parsed.getConfidence().getMethod()).isEqualTo("temperature_scaled");
    }

    @Test
    void mockResponseCarriesModelVersionAndDevice() throws Exception {
        // Contract rule (section 6.2): a mock is identifiable by 0.0.0-mock + DEVICE_MOCK.
        InferenceResponse mock = InferenceResponse.newBuilder()
                .setStatus(InferenceStatus.STATUS_OK)
                .setModelId("rf-classifier").setModelVersion("0.0.0-mock").setModelSha("mock")
                .setDevice(ComputeDevice.DEVICE_MOCK)
                .build();

        InferenceResponse parsed = InferenceResponse.parseFrom(mock.toByteArray());

        assertThat(parsed.getModelVersion()).isEqualTo("0.0.0-mock");
        assertThat(parsed.getDevice()).isEqualTo(ComputeDevice.DEVICE_MOCK);
    }

    @Test
    void batchRequestAndResponseRoundTrip() throws Exception {
        BatchInferenceRequest request = BatchInferenceRequest.newBuilder()
                .addRequests(InferenceRequest.newBuilder().setTrackId("TRK-1").build())
                .addRequests(InferenceRequest.newBuilder().setTrackId("TRK-2").build())
                .build();
        BatchInferenceResponse response = BatchInferenceResponse.newBuilder()
                .addResponses(InferenceResponse.newBuilder().setTrackId("TRK-1")
                        .setStatus(InferenceStatus.STATUS_OK).build())
                .build();

        assertThat(BatchInferenceRequest.parseFrom(request.toByteArray())).isEqualTo(request);
        assertThat(BatchInferenceResponse.parseFrom(response.toByteArray())).isEqualTo(response);
    }

    @Test
    void modelInfoRoundTrips() throws Exception {
        ModelInfo original = ModelInfo.newBuilder()
                .setModelId("rf-classifier").setModelVersion("1.3.0").setModelSha("4a7fc21e")
                .setFramework("onnxruntime-1.20").setTrainedOn("DroneRF v1.0")
                .addLabels("background").addLabels("hovering")
                .putEvalMetrics("f1", 0.94f)
                .setTrainedAt(Timestamp.newBuilder().setSeconds(1_780_000_000).build())
                .setP95LatencyMs(31).setRequiresGpu(false)
                .build();

        ModelInfo parsed = ModelInfo.parseFrom(original.toByteArray());

        assertThat(parsed).isEqualTo(original);
        assertThat(parsed.getEvalMetricsMap()).containsEntry("f1", 0.94f);
        assertThat(parsed.getLabelsList()).containsExactly("background", "hovering");
    }

    @Test
    void modelInfoRequestRoundTrips() throws Exception {
        ModelInfoRequest original = ModelInfoRequest.newBuilder()
                .setModelId("rf-classifier").setModelVersion("1.3.0").build();

        assertThat(ModelInfoRequest.parseFrom(original.toByteArray())).isEqualTo(original);
    }

    @Test
    void healthRequestAndResponseRoundTrip() throws Exception {
        HealthRequest request = HealthRequest.newBuilder().setModelId("rf-classifier").build();
        HealthResponse response = HealthResponse.newBuilder()
                .setStatus(HealthResponse.ServingStatus.SERVING)
                .setDetail("ready").build();

        assertThat(HealthRequest.parseFrom(request.toByteArray())).isEqualTo(request);
        HealthResponse parsedResponse = HealthResponse.parseFrom(response.toByteArray());
        assertThat(parsedResponse).isEqualTo(response);
        assertThat(parsedResponse.getStatus()).isEqualTo(HealthResponse.ServingStatus.SERVING);
    }

    @Test
    void allInferenceStatusValuesPreserved() throws Exception {
        for (InferenceStatus status : InferenceStatus.values()) {
            if (status == InferenceStatus.UNRECOGNIZED) {
                continue;
            }
            InferenceResponse original = InferenceResponse.newBuilder().setStatus(status).build();
            assertThat(InferenceResponse.parseFrom(original.toByteArray()).getStatus())
                    .isEqualTo(status);
        }
    }
}
