package de.jpx3.intave.check.combat.heuristics.combatpatterns.rotation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link FailRotationHeuristic#indicatesFailInjection} — the pure window verdict that
 * marks an artificial fail-rotation processor: a tight aim baseline (≥0.6 on-target ratio) broken by
 * enough (≥3) uniform (peak CV ≤0.35) self-reverting pulses. Pairs with {@link PulseTrackerTest},
 * which exercises the pulse measurement itself.
 */
final class FailRotationHeuristicTest {

  @Test
  void flagsTightBaselineWithUniformPulses() {
    assertTrue(FailRotationHeuristic.indicatesFailInjection(0.8d, 4, 0.2d));
  }

  @Test
  void boundaryValuesCount() {
    assertTrue(FailRotationHeuristic.indicatesFailInjection(0.6d, 3, 0.35d));
  }

  @Test
  void notFlaggedWhenBaselineNotTight() {
    // continuously noisy human aim -> low on-target ratio
    assertFalse(FailRotationHeuristic.indicatesFailInjection(0.5d, 4, 0.2d));
  }

  @Test
  void notFlaggedWithTooFewPulses() {
    assertFalse(FailRotationHeuristic.indicatesFailInjection(0.8d, 2, 0.2d));
  }

  @Test
  void notFlaggedWhenPulsesNotUniform() {
    // genuine misses vary in size -> high peak CV
    assertFalse(FailRotationHeuristic.indicatesFailInjection(0.8d, 4, 0.6d));
  }
}
