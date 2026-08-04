package pk.nust.cuas.track.domain.model;

/**
 * A track's velocity as reported by the latest detection: ground speed and heading.
 *
 * <p>In the foundation this is carried straight through from the detection's kinematics rather than
 * estimated; the Kalman filter derives it from successive positions in M5.2.
 *
 * @param speedMs    ground speed in metres per second
 * @param headingDeg heading in degrees clockwise from true north
 */
public record Velocity(double speedMs, double headingDeg) {

    public static final Velocity NONE = new Velocity(0.0d, 0.0d);
}
