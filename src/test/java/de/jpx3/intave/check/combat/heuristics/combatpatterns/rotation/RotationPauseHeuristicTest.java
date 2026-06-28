package de.jpx3.intave.check.combat.heuristics.combatpatterns.rotation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link RotationPauseHeuristic#isInjectedPause} — the pure tell for a short-stop
 * processor: a tick that was actively turning followed by a tick frozen on both axes (the caller has
 * already established the target is moving, so the freeze is not the target sitting still).
 */
final class RotationPauseHeuristicTest {

  @Test
  void injectedPauseWhenActiveTurnFreezesOnBothAxes() {
    // previous tick turned 8°, this tick fully frozen
    assertTrue(RotationPauseHeuristic.isInjectedPause(8.0f, 0.0f, 0.0f));
  }

  @Test
  void notAPauseWhenStillTurning() {
    // aim keeps moving -> normal tracking, not a freeze
    assertFalse(RotationPauseHeuristic.isInjectedPause(8.0f, 5.0f, 1.0f));
  }

  @Test
  void notAPauseWhenPreviousTickWasNotTurning() {
    // idle aim that simply stays idle (no active turn before the stop) must not flag
    assertFalse(RotationPauseHeuristic.isInjectedPause(0.5f, 0.0f, 0.0f));
  }

  @Test
  void notAPauseWhenOnlyYawFreezesButPitchMoves() {
    // a real correction can null yaw while pitch still tracks -> not a total freeze
    assertFalse(RotationPauseHeuristic.isInjectedPause(8.0f, 0.0f, 1.0f));
  }

  @Test
  void boundaryTurnSpeedCounts() {
    // previous turn exactly at the 2.0° floor, freeze just under the 0.1° epsilon
    assertTrue(RotationPauseHeuristic.isInjectedPause(2.0f, 0.09f, 0.09f));
  }
}
