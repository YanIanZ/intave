package de.jpx3.intave.check.combat.heuristics.combatpatterns.rotation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link PulseTracker} — the pure core of {@link FailRotationHeuristic}.
 *
 * <p>LiquidBounce's {@code FailRotationProcessor} injects deliberate "misses": against an otherwise
 * robotically tight aim (residual to the perfect angle ~0), it adds a bounded horizontal excursion
 * (5–10 deg) for 1–4 ticks and then snaps back. The tracker measures exactly that fingerprint — a
 * tight baseline punctuated by clean, bounded, self-reverting pulses — without flagging a human's
 * continuously noisy aim or a genuine large turn that does not return.
 */
final class PulseTrackerTest {
  private static final double EPS = 1.0e-9d;

  @Test
  void tightConstantAimHasNoPulsesAndFullTinyRatio() {
    PulseTracker tracker = new PulseTracker();
    for (int i = 0; i < 40; i++) {
      tracker.accept(0.2d);
    }
    assertEquals(40, tracker.totalTicks());
    assertEquals(0, tracker.pulseCount());
    assertEquals(1.0d, tracker.tinyTickRatio(), EPS);
  }

  @Test
  void detectsCleanRevertPulsesAgainstTightBaseline() {
    PulseTracker tracker = new PulseTracker();
    // tight tracking (0.3 deg) punctuated by clean 7-8 deg single-tick excursions that snap back
    double[] stream = {
      0.3, 0.3, 0.3, 7.5, 0.3, 0.3, 0.3, 0.3, 8.0, 0.2, 0.3, 0.3, 7.0, 0.3, 0.3, 0.3, 8.2, 0.3, 0.3, 0.3
    };
    for (double residual : stream) {
      tracker.accept(residual);
    }
    assertEquals(4, tracker.pulseCount());
    assertTrue(tracker.tinyTickRatio() > 0.7d, "baseline should stay tight: " + tracker.tinyTickRatio());
    // the four injected peaks are tightly clustered (7.0..8.2) -> low coefficient of variation
    assertTrue(tracker.peakMagnitudeCv() < 0.2d, "expected uniform peaks, got CV " + tracker.peakMagnitudeCv());
  }

  @Test
  void multiTickPulseThatRevertsWithinWindowCounts() {
    PulseTracker tracker = new PulseTracker();
    double[] stream = {0.3, 0.3, 6.0, 8.0, 6.5, 0.3, 0.3, 0.3};
    for (double residual : stream) {
      tracker.accept(residual);
    }
    assertEquals(1, tracker.pulseCount());
  }

  @Test
  void genuineLargeTurnThatDoesNotRevertIsNotAPulse() {
    PulseTracker tracker = new PulseTracker();
    // residual climbs and stays high (a real sustained turn / acquisition) -> not a fail pulse
    double[] stream = {0.3, 0.3, 30.0, 28.0, 25.0, 22.0, 18.0, 15.0};
    for (double residual : stream) {
      tracker.accept(residual);
    }
    assertEquals(0, tracker.pulseCount());
  }

  @Test
  void continuouslyNoisyHumanAimHasLowTinyRatio() {
    PulseTracker tracker = new PulseTracker();
    double[] stream = {2.5, 3.1, 1.8, 4.0, 2.2, 3.7, 2.9, 1.6, 3.3, 2.1, 4.2, 2.8};
    for (double residual : stream) {
      tracker.accept(residual);
    }
    // human error is continuous mid-range noise, not a tight baseline with clean pulses
    assertTrue(tracker.tinyTickRatio() < 0.5d, "noisy aim should not look tight: " + tracker.tinyTickRatio());
    assertEquals(0, tracker.pulseCount());
  }

  @Test
  void resetClearsState() {
    PulseTracker tracker = new PulseTracker();
    tracker.accept(0.3d);
    tracker.accept(8.0d);
    tracker.accept(0.3d);
    tracker.reset();
    assertEquals(0, tracker.totalTicks());
    assertEquals(0, tracker.pulseCount());
  }
}
