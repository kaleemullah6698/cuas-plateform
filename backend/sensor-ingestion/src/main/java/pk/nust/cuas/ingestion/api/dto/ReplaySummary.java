package pk.nust.cuas.ingestion.api.dto;

/**
 * The result of a dataset replay: how many detections were read, and how many of those were
 * accepted into the pipeline versus rejected (malformed or invalid).
 *
 * <p>Only counts are returned, not per-line detail: a replay dataset can be large, and the
 * per-record reasons are logged server-side. "Accepted" means published to the raw topic, the same
 * meaning it has for the batch endpoint.
 *
 * @param total    detections read from the dataset
 * @param accepted detections published to the raw topic
 * @param rejected detections dropped as malformed or invalid
 */
public record ReplaySummary(int total, int accepted, int rejected) {
}
