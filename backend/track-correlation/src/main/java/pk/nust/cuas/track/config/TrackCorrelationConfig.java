package pk.nust.cuas.track.config;

import java.security.SecureRandom;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import pk.nust.cuas.commons.id.Uuidv7Generator;
import pk.nust.cuas.track.common.TrackProtoMapper;
import pk.nust.cuas.track.domain.model.GeoPoint;
import pk.nust.cuas.track.domain.model.ProtectedAsset;
import pk.nust.cuas.track.domain.port.ProtectedAssetRegistry;
import pk.nust.cuas.track.domain.service.AssociationPolicy;
import pk.nust.cuas.track.domain.service.CpaCalculator;
import pk.nust.cuas.track.domain.service.KalmanFilter;
import pk.nust.cuas.track.domain.service.SectorPolicy;
import pk.nust.cuas.track.domain.service.TrackConfirmationPolicy;
import pk.nust.cuas.track.domain.service.TrackMergePolicy;
import pk.nust.cuas.track.domain.service.TrackOutlookCalculator;
import pk.nust.cuas.track.domain.service.TrackTimeoutPolicy;
import pk.nust.cuas.track.domain.service.TrajectoryPredictor;
import pk.nust.cuas.track.infrastructure.config.ConfiguredProtectedAssetRegistry;

/**
 * Wires the framework-free domain services as beans, parameterised from {@link CorrelationProperties},
 * and enables the scheduler behind the timeout sweep.
 *
 * <p>The domain classes carry no Spring annotations — that is what lets the domain compile without
 * Spring (CLAUDE.md section 2) — so they are constructed here.
 */
@Configuration
@EnableConfigurationProperties(CorrelationProperties.class)
@EnableScheduling
public class TrackCorrelationConfig {

    /** UTC, for association, prediction, and record timestamps. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public SectorPolicy sectorPolicy(CorrelationProperties properties) {
        return new SectorPolicy(properties.sector().cellDegrees());
    }

    @Bean
    public AssociationPolicy associationPolicy(CorrelationProperties properties) {
        return new AssociationPolicy(properties.association().distanceGateM());
    }

    @Bean
    public TrackConfirmationPolicy trackConfirmationPolicy(CorrelationProperties properties) {
        return new TrackConfirmationPolicy(properties.confirmation().threshold());
    }

    @Bean
    public TrackTimeoutPolicy trackTimeoutPolicy(CorrelationProperties properties) {
        return new TrackTimeoutPolicy(
                properties.lifecycle().coastTimeout(), properties.lifecycle().lostTimeout());
    }

    @Bean
    public TrackMergePolicy trackMergePolicy(CorrelationProperties properties) {
        return new TrackMergePolicy(
                properties.merge().gateM(), properties.merge().requiredConsecutiveSweeps());
    }

    @Bean
    public KalmanFilter kalmanFilter(CorrelationProperties properties) {
        CorrelationProperties.Kalman k = properties.kalman();
        return new KalmanFilter(k.processNoise(), k.measurementNoise(),
                k.initialPositionVariance(), k.initialVelocityVariance(), k.initialAccelerationVariance());
    }

    @Bean
    public TrajectoryPredictor trajectoryPredictor(KalmanFilter kalman, CorrelationProperties properties) {
        return new TrajectoryPredictor(
                kalman, properties.prediction().horizon(), properties.prediction().step());
    }

    @Bean
    public CpaCalculator cpaCalculator() {
        return new CpaCalculator();
    }

    /**
     * The interim asset source (section 4.1.12): a static list from configuration until the
     * Configuration Service exists at M8, at which point only this method changes.
     */
    @Bean
    public ProtectedAssetRegistry protectedAssetRegistry(CorrelationProperties properties) {
        return new ConfiguredProtectedAssetRegistry(properties.assets().stream()
                .map(asset -> new ProtectedAsset(asset.id(), asset.name(),
                        new GeoPoint(asset.latitude(), asset.longitude(), asset.altitudeM())))
                .toList());
    }

    @Bean
    public TrackOutlookCalculator trackOutlookCalculator(TrajectoryPredictor predictor,
                                                         CpaCalculator cpaCalculator,
                                                         ProtectedAssetRegistry assets) {
        return new TrackOutlookCalculator(predictor, cpaCalculator, assets);
    }

    /**
     * The one domain-to-contract mapping, shared by the Kafka producer and the gRPC stream so the
     * two surfaces cannot describe the same track differently.
     */
    @Bean
    public TrackProtoMapper trackProtoMapper(KalmanFilter kalman) {
        return new TrackProtoMapper(kalman);
    }

    @Bean
    public Uuidv7Generator uuidv7Generator() {
        return new Uuidv7Generator(new SecureRandom());
    }
}
