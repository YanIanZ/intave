package de.jpx3.intave.check.combat.heuristics.other;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link AttackThroughWallHeuristic#isThroughWall(double, double)} — the pure tell behind
 * wallbang kill-aura: a swing that is a valid hit-box hit when terrain is ignored but whose identical ray
 * is stopped by a block before reaching the entity. Reach sentinels: {@code 10} = no hit (blocked / out
 * of range), {@code -1} = ray missed the hit-box, {@code >= 0} = a hit. The sustained-run/release side is
 * covered by {@code SustainedStreakDetectorTest}.
 */
final class AttackThroughWallHeuristicTest {

  @Test
  void validHitBlockedByTerrainIsThroughWall() {
    // hits the box at reach 3 ignoring blocks, but the same ray is blocked respecting them
    assertTrue(AttackThroughWallHeuristic.isThroughWall(3.0d, 10.0d));
  }

  @Test
  void insideHitboxButBlockedStillCounts() {
    // reach 0 == inside the box ignoring blocks, blocked respecting them
    assertTrue(AttackThroughWallHeuristic.isThroughWall(0.0d, 10.0d));
  }

  @Test
  void clearLineOfSightIsNotThroughWall() {
    // both traces reach the box — nothing in the way
    assertFalse(AttackThroughWallHeuristic.isThroughWall(3.0d, 3.0d));
  }

  @Test
  void plainMissIsNotThroughWall() {
    // the swing never hit the box even ignoring blocks — just a miss, never flagged
    assertFalse(AttackThroughWallHeuristic.isThroughWall(10.0d, 10.0d));
  }

  @Test
  void rayMissingTheHitboxIsNotThroughWall() {
    // ignoring-blocks ray passed but missed the hit-box (-1) — not a valid hit, so not a wallbang
    assertFalse(AttackThroughWallHeuristic.isThroughWall(-1.0d, 10.0d));
  }

  @Test
  void hitThatReachesPastTerrainIsNotThroughWall() {
    // respecting-blocks trace also reached (just missed the box) — terrain did not block the line
    assertFalse(AttackThroughWallHeuristic.isThroughWall(3.0d, -1.0d));
  }
}
