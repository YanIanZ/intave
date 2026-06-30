package de.jpx3.intave.check.combat.heuristics.other;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link BaritoneHeuristic#withinPathingRecency} — the pure tell that an attack happens
 * while the pathfinder's robotic heading-lock window was stamped recently (bot-pathing while
 * fighting). The streak/release side is covered by {@code SustainedStreakDetectorTest}.
 */
final class BaritoneHeuristicTest {

  @Test
  void neverPathedNeverMatches() {
    assertFalse(BaritoneHeuristic.withinPathingRecency(0L, 10_000L));
  }

  @Test
  void attackShortlyAfterHeadingLockMatches() {
    assertTrue(BaritoneHeuristic.withinPathingRecency(1_000L, 2_000L)); // 1s later
  }

  @Test
  void boundaryIsInclusive() {
    assertTrue(BaritoneHeuristic.withinPathingRecency(1_000L, 2_500L)); // exactly 1500ms
  }

  @Test
  void staleHeadingLockDoesNotMatch() {
    assertFalse(BaritoneHeuristic.withinPathingRecency(1_000L, 2_600L)); // 1600ms — too old
  }
}
