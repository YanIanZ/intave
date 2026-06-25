package de.jpx3.intave.check.combat.heuristics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for {@link RollingCorrelation} — the engine's online (Welford) Pearson-correlation
 * accumulator that {@code RotationLinearityHeuristic} uses to spot collinear (Δyaw, Δpitch) aim paths.
 */
final class RollingCorrelationTest {

  private static final double EPS = 1.0e-9d;

  @Test
  void emptyReportsZero() {
    RollingCorrelation correlation = new RollingCorrelation();
    assertEquals(0L, correlation.count());
    assertEquals(0.0d, correlation.correlation(), EPS);
  }

  @Test
  void singlePairReportsZero() {
    RollingCorrelation correlation = new RollingCorrelation();
    correlation.accept(1.0d, 1.0d);
    assertEquals(1L, correlation.count());
    assertEquals(0.0d, correlation.correlation(), EPS);
  }

  @Test
  void perfectlyCollinearPositiveReportsOne() {
    RollingCorrelation correlation = new RollingCorrelation();
    correlation.accept(1.0d, 2.0d);
    correlation.accept(2.0d, 4.0d);
    correlation.accept(3.0d, 6.0d);
    // y = 2x — a perfectly straight path
    assertEquals(1.0d, correlation.correlation(), EPS);
  }

  @Test
  void perfectlyCollinearNegativeReportsMinusOne() {
    RollingCorrelation correlation = new RollingCorrelation();
    correlation.accept(1.0d, 6.0d);
    correlation.accept(2.0d, 4.0d);
    correlation.accept(3.0d, 2.0d);
    // y = -2x + 8 — perfectly straight, opposite direction
    assertEquals(-1.0d, correlation.correlation(), EPS);
  }

  @Test
  void degenerateAxisReportsZero() {
    RollingCorrelation correlation = new RollingCorrelation();
    correlation.accept(1.0d, 5.0d);
    correlation.accept(2.0d, 5.0d);
    correlation.accept(3.0d, 5.0d);
    // one axis has no variance — Pearson undefined, reported as 0 (not a spurious extreme)
    assertEquals(0.0d, correlation.correlation(), EPS);
  }

  @Test
  void resetClearsState() {
    RollingCorrelation correlation = new RollingCorrelation();
    correlation.accept(1.0d, 2.0d);
    correlation.accept(2.0d, 4.0d);
    correlation.reset();
    assertEquals(0L, correlation.count());
    assertEquals(0.0d, correlation.correlation(), EPS);
  }
}
