package de.jpx3.intave.check.combat.heuristics.other;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link MultiActionHeuristic}'s pure overlap tells — placing while a break is in
 * progress, and starting a break while an item is in active use (LiquidBounce {@code MultiActions}).
 * Vanilla allows one hand action per tick; creative (instant break) is exempt. The streak/release
 * side is covered by {@code SustainedStreakDetectorTest}.
 */
final class MultiActionHeuristicTest {

  @Test
  void placeWhileBreakingIsAnOverlap() {
    assertTrue(MultiActionHeuristic.placeOverlapsBreak(false, true));
  }

  @Test
  void placeWithoutActiveBreakIsFine() {
    assertFalse(MultiActionHeuristic.placeOverlapsBreak(false, false));
  }

  @Test
  void creativeIsExemptFromPlaceOverlap() {
    // creative does instant breaks, so a place during one is legitimate
    assertFalse(MultiActionHeuristic.placeOverlapsBreak(true, true));
  }

  @Test
  void breakStartWhileUsingItemIsAnOverlap() {
    assertTrue(MultiActionHeuristic.digStartOverlapsUse(false, true, true));
  }

  @Test
  void nonStartDigDoesNotOverlap() {
    // STOP/ABORT_DESTROY while using an item is not the exploit
    assertFalse(MultiActionHeuristic.digStartOverlapsUse(false, false, true));
  }

  @Test
  void breakStartWithoutItemUseIsFine() {
    assertFalse(MultiActionHeuristic.digStartOverlapsUse(false, true, false));
  }

  @Test
  void creativeIsExemptFromDigOverlap() {
    assertFalse(MultiActionHeuristic.digStartOverlapsUse(true, true, true));
  }

  @Test
  void placeWhileUsingItemIsAnOverlap() {
    // the right-click is occupied by the item use, so a simultaneous place is impossible
    assertTrue(MultiActionHeuristic.placeOverlapsUse(false, true));
  }

  @Test
  void placeWithoutItemUseIsFine() {
    assertFalse(MultiActionHeuristic.placeOverlapsUse(false, false));
  }

  @Test
  void creativeIsExemptFromPlaceWhileUse() {
    assertFalse(MultiActionHeuristic.placeOverlapsUse(true, true));
  }
}
