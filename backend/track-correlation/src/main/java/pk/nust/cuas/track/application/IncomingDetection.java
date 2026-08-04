package pk.nust.cuas.track.application;

import pk.nust.cuas.track.domain.model.TrackState;

/**
 * A normalised detection projected into what track correlation needs from it: the observed state to
 * associate, plus the sensor sequence and correlation id that are carried onto the association
 * record and the published track event.
 *
 * @param state         the observed state (position, velocity, time, detection, sensor)
 * @param sensorSeq     the sensor's sequence number, kept on the association record
 * @param correlationId the end-to-end trace carried from the detection to the track event
 */
public record IncomingDetection(TrackState state, long sensorSeq, String correlationId) {
}
