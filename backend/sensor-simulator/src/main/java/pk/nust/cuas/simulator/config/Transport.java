package pk.nust.cuas.simulator.config;

/** Which real ingestion intake(s) the simulator drives (ARCHITECTURE.md section 4.1.4). */
public enum Transport {
    /** The REST batch endpoint, {@code POST /ingest/detections}. */
    REST,
    /** The bidirectional gRPC stream, {@code DetectionIngestService.StreamDetections}. */
    GRPC,
    /** Both, so one run exercises each intake. */
    BOTH
}
