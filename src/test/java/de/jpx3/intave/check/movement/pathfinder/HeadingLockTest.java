package de.jpx3.intave.check.movement.pathfinder;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link HeadingLock#indicatesHeadingLock} — the pure window verdict that flags a
 * pathfinding bot: the player genuinely turned (≥45° cumulative) yet kept the transmitted yaw welded
 * to the travel heading (mean residual ≤3°, spread ≤3°).
 */
final class HeadingLockTest {

  @Test
  void roboticWhenTurnedHardButResidualStaysPinned() {
    assertTrue(HeadingLock.indicatesHeadingLock(60d, 2d, 2d));
  }

  @Test
  void boundaryValuesCount() {
    assertTrue(HeadingLock.indicatesHeadingLock(45d, 3d, 3d));
  }

  @Test
  void notRoboticWhenPlayerBarelyTurned() {
    // a straight-line sprinter has yaw≈heading too, so a low total turn must not flag
    assertFalse(HeadingLock.indicatesHeadingLock(30d, 1d, 1d));
  }

  @Test
  void notRoboticWhenResidualTooLarge() {
    assertFalse(HeadingLock.indicatesHeadingLock(60d, 5d, 1d));
  }

  @Test
  void notRoboticWhenResidualSpreadTooLarge() {
    // a human overshoots/corrects through a turn -> high spread even if mean is low
    assertFalse(HeadingLock.indicatesHeadingLock(60d, 1d, 5d));
  }
}
