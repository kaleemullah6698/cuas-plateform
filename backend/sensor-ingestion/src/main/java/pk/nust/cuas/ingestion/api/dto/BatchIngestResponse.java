package pk.nust.cuas.ingestion.api.dto;

import java.util.List;

/**
 * The response of {@code POST /ingest/detections}: how many detections were accepted for the
 * pipeline and the detail of any that were not.
 *
 * <p>"Accepted" means published to the raw topic; normalisation happens asynchronously afterwards,
 * so this is an intake receipt, not a normalisation result.
 *
 * @param accepted   the number published to the raw topic
 * @param rejected   the number rejected at intake
 * @param rejections the per-detection rejection detail
 */
public record BatchIngestResponse(int accepted, int rejected, List<RejectedDetection> rejections) {

    public BatchIngestResponse {
        rejections = List.copyOf(rejections);
    }
}
