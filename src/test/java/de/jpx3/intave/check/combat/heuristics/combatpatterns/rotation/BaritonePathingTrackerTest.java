package de.jpx3.intave.check.combat.heuristics.combatpatterns.rotation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link BaritonePathingTracker} — the pure core of
 * {@link BaritonePathingRotationHeuristic}.
 *
 * <p>A pathing bot (Baritone) walks a route with the pitch held dead still, steering only in yaw to
 * follow the node chain. The tracker measures exactly that: a near-zero pitch range carried by only a
 * couple of distinct pitch steps while a large amount of yaw turning accumulates. A human who sweeps
 * that much yaw tilts the view, so their pitch range and distinct-step count are far higher.
 */
final class BaritonePathingTrackerTest {
  private static final double EPS = 1.0e-9d;

  @Test
  void frozenPitchWhileTurningLooksLikeABot() {
    BaritonePathingTracker tracker = new BaritonePathingTracker();
    // 40 travelling ticks: pitch never moves, yaw steadily steers the path
    for (int i = 0; i < 40; i++) {
      tracker.accept(1.5d, 0.0d);
    }
    assertEquals(40, tracker.count());
    assertEquals(0.0d, tracker.pitchRange(), EPS);
    assertEquals(1, tracker.distinctPitchDeltas());
    assertEquals(60.0d, tracker.yawTurnSum(), EPS);
  }

  @Test
  void tinyPitchNoiseUnderHalfABinStillCollapsesToOneStep() {
    BaritonePathingTracker tracker = new BaritonePathingTracker();
    // sub-quantum jitter (|Δpitch| < 0.005) a bot's float rounding can produce — one distinct bin
    double[] pitch = {0.0d, 0.001d, -0.001d, 0.002d, -0.002d, 0.0d};
    for (double p : pitch) {
      tracker.accept(2.0d, p);
    }
    assertEquals(1, tracker.distinctPitchDeltas());
    assertTrue(tracker.pitchRange() < 0.01d, "range: " + tracker.pitchRange());
  }

  @Test
  void humanLikeAimHasWidePitchRangeAndManyDistinctSteps() {
    BaritonePathingTracker tracker = new BaritonePathingTracker();
    // a human turning also tilts the view: pitch wanders across many bins
    double[] pitch = {0.4d, -0.3d, 0.9d, -0.6d, 1.2d, -0.2d, 0.7d, -1.1d, 0.5d, -0.8d};
    for (double p : pitch) {
      tracker.accept(3.0d, p);
    }
    assertTrue(tracker.distinctPitchDeltas() >= 8, "distinct: " + tracker.distinctPitchDeltas());
    assertTrue(tracker.pitchRange() > 1.5d, "range: " + tracker.pitchRange());
  }

  @Test
  void spreadPitchDeltasOccupyDistinctBins() {
    BaritonePathingTracker tracker = new BaritonePathingTracker();
    double[] pitch = {0.0d, 0.05d, 0.10d, 0.20d};
    for (double p : pitch) {
      tracker.accept(1.0d, p);
    }
    assertEquals(4, tracker.distinctPitchDeltas());
    assertEquals(0.20d, tracker.pitchRange(), EPS);
  }

  @Test
  void yawTurnSumAccumulatesAbsoluteTurning() {
    BaritonePathingTracker tracker = new BaritonePathingTracker();
    tracker.accept(5.0d, 0.0d);
    tracker.accept(3.0d, 0.0d);
    tracker.accept(2.0d, 0.0d);
    assertEquals(10.0d, tracker.yawTurnSum(), EPS);
  }

  @Test
  void resetClearsState() {
    BaritonePathingTracker tracker = new BaritonePathingTracker();
    tracker.accept(5.0d, 0.5d);
    tracker.accept(5.0d, 0.0d);
    tracker.reset();
    assertEquals(0, tracker.count());
    assertEquals(0, tracker.distinctPitchDeltas());
    assertEquals(0.0d, tracker.yawTurnSum(), EPS);
    assertEquals(0.0d, tracker.pitchRange(), EPS);
  }
}
