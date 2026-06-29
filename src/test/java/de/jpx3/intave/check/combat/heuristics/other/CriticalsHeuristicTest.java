package de.jpx3.intave.check.combat.heuristics.other;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link CriticalsHeuristic#isSpoofedCritState(boolean, boolean, double, double)} — the
 * pure tell behind packet/spoofed criticals (Wurst {@code Criticals} PACKET mode, LiquidBounce packet
 * criticals): the client claims airborne to pass the server's crit test while collision proves it is
 * genuinely grounded with no real fall. The sustained-run/release side is covered by
 * {@code SustainedStreakDetectorTest}.
 */
final class CriticalsHeuristicTest {

  @Test
  void claimsAirborneWhileGroundedWithNoMotionIsSpoofed() {
    assertTrue(CriticalsHeuristic.isSpoofedCritState(false, true, 0.0d, 0.0d));
  }

  @Test
  void genuinelyAirborneIsNotSpoofed() {
    // collision agrees the player is off the ground — a real fall arc, the legitimate way to crit
    assertFalse(CriticalsHeuristic.isSpoofedCritState(false, false, -0.3d, 1.5d));
  }

  @Test
  void honestGroundedAttackIsNotSpoofed() {
    // claiming on-ground while grounded is the normal, non-crit attack
    assertFalse(CriticalsHeuristic.isSpoofedCritState(true, true, 0.0d, 0.0d));
  }

  @Test
  void realUpwardMotionOnLaunchTickIsNotSpoofed() {
    // the tick a player actually jumps carries real vertical motion — must not be flagged
    assertFalse(CriticalsHeuristic.isSpoofedCritState(false, true, 0.42d, 0.0d));
  }

  @Test
  void accumulatedRealFallIsNotSpoofed() {
    // genuine fall distance present — guard against flagging a real descent
    assertFalse(CriticalsHeuristic.isSpoofedCritState(false, true, 0.0d, 0.5d));
  }

  @Test
  void tinyNegativeMotionStillCountsViaAbsoluteValue() {
    assertTrue(CriticalsHeuristic.isSpoofedCritState(false, true, -0.01d, 0.0d));
  }

  @Test
  void motionYExactlyAtBoundaryIsNotSpoofed() {
    // boundary is strict (< 0.02): motion sitting on the threshold must not flag
    assertFalse(CriticalsHeuristic.isSpoofedCritState(false, true, 0.02d, 0.0d));
  }
}
