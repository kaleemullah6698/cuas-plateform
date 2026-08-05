package pk.nust.cuas.orchestrator.domain.service;

import java.time.Duration;

/**
 * Samples a realistic inference time for a service (ARCHITECTURE.md section 6.4, "samples a lognormal
 * distribution matched to each service's documented p95 budget").
 *
 * <p>Lognormal rather than uniform or constant, because inference latency is: mostly clustered near a
 * median with a long right tail from queueing, batching and the occasional cache miss. A constant
 * would make every p95 dashboard, every timeout, and every latency-budget assertion meaningless — the
 * system would look better-behaved under mock than it ever will under a real model.
 *
 * <p>The distribution is fitted so the p95 lands on the service's configured budget: with
 * {@code sigma} fixed, {@code mu = ln(p95) - 1.645 * sigma}, 1.645 being the standard normal's 95th
 * percentile. A service's timeout is therefore hit occasionally but not usually, which is exactly the
 * condition the circuit breaker needs to be exercised against.
 *
 * <p>Deterministic: the sample comes from a seed built out of the track and service ids, so the same
 * track always takes the same time on the same service.
 *
 * <p>Pure and framework-free.
 */
public final class LatencyModel {

    /** The standard normal's 95th percentile — the z that makes the fit land p95 on the budget. */
    private static final double Z95 = 1.6448536269514722d;

    private final double sigma;

    /**
     * @param sigma the lognormal shape; larger means a heavier tail. Around 0.35 gives a realistic
     *              spread where the median sits near 60% of p95.
     * @throws IllegalArgumentException when sigma is not positive
     */
    public LatencyModel(double sigma) {
        if (!(sigma > 0.0d)) {
            throw new IllegalArgumentException("sigma must be positive, was " + sigma);
        }
        this.sigma = sigma;
    }

    /**
     * @param p95Budget the service's documented p95 latency budget
     * @param seedParts identifiers to seed from, e.g. track id and service id
     * @return a sampled inference time, never negative and never zero
     */
    public Duration sample(Duration p95Budget, String... seedParts) {
        double p95Millis = Math.max(1.0d, p95Budget.toMillis());
        double mu = Math.log(p95Millis) - Z95 * sigma;

        double uniform = DeterministicSeed.toUnitInterval(DeterministicSeed.of(seedParts));
        // Inverse-transform sampling: clamp away from the open interval's ends, since the normal
        // quantile diverges at 0 and 1 and would otherwise produce a zero or absurd latency.
        double clamped = Math.min(Math.max(uniform, 1e-6d), 1.0d - 1e-6d);
        double millis = Math.exp(mu + sigma * standardNormalQuantile(clamped));

        return Duration.ofNanos(Math.max(1L, Math.round(millis * 1_000_000.0d)));
    }

    /**
     * The standard normal inverse CDF, by Acklam's rational approximation.
     *
     * <p>Accurate to about 1.15e-9 across the whole interval — far beyond what a latency simulator
     * needs, and it avoids pulling in a statistics dependency for one function.
     */
    private static double standardNormalQuantile(double p) {
        final double[] a = {-3.969683028665376e+01, 2.209460984245205e+02, -2.759285104469687e+02,
                1.383577518672690e+02, -3.066479806614716e+01, 2.506628277459239e+00};
        final double[] b = {-5.447609879822406e+01, 1.615858368580409e+02, -1.556989798598866e+02,
                6.680131188771972e+01, -1.328068155288572e+01};
        final double[] c = {-7.784894002430293e-03, -3.223964580411365e-01, -2.400758277161838e+00,
                -2.549732539343734e+00, 4.374664141464968e+00, 2.938163982698783e+00};
        final double[] d = {7.784695709041462e-03, 3.224671290700398e-01, 2.445134137142996e+00,
                3.754408661907416e+00};
        final double pLow = 0.02425d;
        final double pHigh = 1.0d - pLow;

        if (p < pLow) {
            double q = Math.sqrt(-2 * Math.log(p));
            return (((((c[0] * q + c[1]) * q + c[2]) * q + c[3]) * q + c[4]) * q + c[5])
                    / ((((d[0] * q + d[1]) * q + d[2]) * q + d[3]) * q + 1);
        }
        if (p > pHigh) {
            double q = Math.sqrt(-2 * Math.log(1 - p));
            return -(((((c[0] * q + c[1]) * q + c[2]) * q + c[3]) * q + c[4]) * q + c[5])
                    / ((((d[0] * q + d[1]) * q + d[2]) * q + d[3]) * q + 1);
        }
        double q = p - 0.5d;
        double r = q * q;
        return (((((a[0] * r + a[1]) * r + a[2]) * r + a[3]) * r + a[4]) * r + a[5]) * q
                / (((((b[0] * r + b[1]) * r + b[2]) * r + b[3]) * r + b[4]) * r + 1);
    }
}
