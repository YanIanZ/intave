package de.jpx3.intave.check.combat.heuristics.other;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link InventoryCloseAttackHeuristic#withinCloseWindow} — the pure timing tell that
 * an attack landed within one tick of a real container close (the {@code simulateInventoryClosing}
 * signature). The streak/release side is covered by {@code SustainedStreakDetectorTest}.
 */
final class InventoryCloseAttackHeuristicTest {

  @Test
  void noCloseArmedNeverMatches() {
    assertFalse(InventoryCloseAttackHeuristic.withinCloseWindow(0L, 5_000L));
  }

  @Test
  void attackJustAfterCloseMatches() {
    // close at 1000ms, attack 50ms later — super-human close-then-attack
    assertTrue(InventoryCloseAttackHeuristic.withinCloseWindow(1_000L, 1_050L));
  }

  @Test
  void boundaryIsInclusive() {
    // exactly 75ms (~1.5 ticks)
    assertTrue(InventoryCloseAttackHeuristic.withinCloseWindow(1_000L, 1_075L));
  }

  @Test
  void attackTooLongAfterCloseDoesNotMatch() {
    // 76ms — a deliberate human close-then-aim-then-click takes far longer
    assertFalse(InventoryCloseAttackHeuristic.withinCloseWindow(1_000L, 1_076L));
  }
}
