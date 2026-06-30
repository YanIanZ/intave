package de.jpx3.intave.check.combat.heuristics.combatpatterns.rotation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link BaritonePathingRotationHeuristic#isMachinePathing(double, int, double)} — the
 * pure tell separating a pathing bot's rotation window (frozen pitch + few distinct steps + lots of
 * yaw turning) from natural travel. The decaying-evidence/release side is covered by
 * {@code ConfidenceBufferTest}; the window accumulation by {@code BaritonePathingTrackerTest}.
 */
final class BaritonePathingRotationHeuristicTest {

  @Test
  void frozenPitchWithHeavyYawTurningIsABot() {
    assertTrue(BaritonePathingRotationHeuristic.isMachinePathing(0.0d, 1, 60.0d));
  }

  @Test
  void wanderingPitchIsNotABotEvenWhileTurning() {
    // a human sweeping the same yaw tilts the view — wide pitch range breaks the tell
    assertFalse(BaritonePathingRotationHeuristic.isMachinePathing(0.5d, 1, 60.0d));
  }

  @Test
  void tooManyDistinctPitchStepsIsNotABot() {
    assertFalse(BaritonePathingRotationHeuristic.isMachinePathing(0.0d, 9, 60.0d));
  }

  @Test
  void walkingStraightWithoutTurningIsNotABot() {
    // frozen pitch but barely any yaw turning — just travelling forward, not steering a path
    assertFalse(BaritonePathingRotationHeuristic.isMachinePathing(0.0d, 1, 5.0d));
  }

  @Test
  void rangeExactlyAtTheBoundaryIsNotFlagged() {
    // boundary is strict (< 0.02): a range sitting on the threshold must not flag
    assertFalse(BaritonePathingRotationHeuristic.isMachinePathing(0.02d, 1, 60.0d));
  }

  @Test
  void yawSumExactlyAtTheBoundaryFlags() {
    // threshold is inclusive (>= 30): exactly the minimum steering still counts
    assertTrue(BaritonePathingRotationHeuristic.isMachinePathing(0.0d, 1, 30.0d));
  }
}
