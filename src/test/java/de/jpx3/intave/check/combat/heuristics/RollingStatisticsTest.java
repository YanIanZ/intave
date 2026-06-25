package de.jpx3.intave.check.combat.heuristics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for {@link RollingStatistics} — the engine's online (Welford) mean/variance/CV
 * accumulator that the rotation heuristics (constant-speed, smoothing) build on.
 */
final class RollingStatisticsTest {

  private static final double EPS = 1.0e-9d;

  @Test
  void emptyBufferIsMaximallyNonUniform() {
    RollingStatistics stats = new RollingStatistics();
    assertEquals(0L, stats.count());
    assertEquals(0.0d, stats.mean(), EPS);
    assertEquals(0.0d, stats.variance(), EPS);
    // a non-positive mean / empty buffer reports MAX_VALUE so a *low* CV unambiguously means robotic
    assertEquals(Double.MAX_VALUE, stats.coefficientOfVariation());
  }

  @Test
  void computesPopulationMeanVarianceAndCv() {
    RollingStatistics stats = new RollingStatistics();
    stats.accept(2.0d);
    stats.accept(4.0d);
    stats.accept(6.0d);

    assertEquals(3L, stats.count());
    assertEquals(4.0d, stats.mean(), EPS);
    // population variance ((-2)^2 + 0 + 2^2) / 3 = 8/3
    assertEquals(8.0d / 3.0d, stats.variance(), EPS);
    assertEquals(Math.sqrt(8.0d / 3.0d), stats.standardDeviation(), EPS);
    assertEquals(Math.sqrt(8.0d / 3.0d) / 4.0d, stats.coefficientOfVariation(), EPS);
  }

  @Test
  void constantStreamHasZeroVariation() {
    RollingStatistics stats = new RollingStatistics();
    for (int i = 0; i < 4; i++) {
      stats.accept(5.0d);
    }
    assertEquals(5.0d, stats.mean(), EPS);
    assertEquals(0.0d, stats.variance(), EPS);
    assertEquals(0.0d, stats.coefficientOfVariation(), EPS);
  }

  @Test
  void singleSampleHasZeroVariance() {
    RollingStatistics stats = new RollingStatistics();
    stats.accept(5.0d);
    assertEquals(1L, stats.count());
    assertEquals(0.0d, stats.variance(), EPS);
    assertEquals(0.0d, stats.coefficientOfVariation(), EPS);
  }

  @Test
  void resetClearsState() {
    RollingStatistics stats = new RollingStatistics();
    stats.accept(1.0d);
    stats.accept(9.0d);
    stats.reset();
    assertEquals(0L, stats.count());
    assertEquals(0.0d, stats.mean(), EPS);
    assertEquals(Double.MAX_VALUE, stats.coefficientOfVariation());
  }
}
