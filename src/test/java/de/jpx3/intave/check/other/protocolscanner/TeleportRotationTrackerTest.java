/*
 * Copyright 2026 Intave
 *
 * This software is licensed under the PolyForm Perimeter License 1.0.0.
 * You may use this software for any purpose, except for providing to
 * others any product that competes with the software.
 *
 * A copy of the license is available at:
 *   https://polyformproject.org/licenses/perimeter/1.0.0/
 */

package de.jpx3.intave.check.other.protocolscanner;

import org.junit.jupiter.api.Test;

import static de.jpx3.intave.check.other.protocolscanner.TeleportRotationTracker.Observation.ADOPTED;
import static de.jpx3.intave.check.other.protocolscanner.TeleportRotationTracker.Observation.EXPIRED;
import static de.jpx3.intave.check.other.protocolscanner.TeleportRotationTracker.Observation.FLAGGED;
import static de.jpx3.intave.check.other.protocolscanner.TeleportRotationTracker.Observation.IGNORED;
import static de.jpx3.intave.check.other.protocolscanner.TeleportRotationTracker.Observation.PENDING;
import static de.jpx3.intave.check.other.protocolscanner.TeleportRotationTracker.Observation.REFUSED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TeleportRotationTrackerTest {
  private static TeleportRotationTracker tracker(int requiredRefusals, int timeoutPackets) {
    return new TeleportRotationTracker(
      requiredRefusals, timeoutPackets, 1000L, 30000L, 0.05f, 30.0f
    );
  }

  @Test
  void armRejectsSmallServerDelta() {
    TeleportRotationTracker tracker = tracker(1, 20);

    assertFalse(tracker.arm(1, 10.0f, 0.0f, 0.0f, 0.0f, 0L));
    assertEquals(IGNORED, tracker.observe(10.0f, 0.0f, 1L));
  }

  @Test
  void armRejectsNonFiniteExpectedRotation() {
    TeleportRotationTracker tracker = tracker(1, 20);

    assertFalse(tracker.arm(1, Float.NaN, 0.0f, 0.0f, 0.0f, 0L));
  }

  @Test
  void adoptThenRevertFlagsAfterRequiredRefusals() {
    TeleportRotationTracker tracker = tracker(2, 20);

    assertTrue(tracker.arm(1, 90.0f, 0.0f, 0.0f, 0.0f, 0L));
    assertEquals(ADOPTED, tracker.observe(90.0f, 0.0f, 1L));
    assertEquals(REFUSED, tracker.observe(0.0f, 0.0f, 2L));

    assertTrue(tracker.arm(2, 90.0f, 0.0f, 0.0f, 0.0f, 3L));
    assertEquals(ADOPTED, tracker.observe(90.0f, 0.0f, 4L));
    assertEquals(FLAGGED, tracker.observe(0.0f, 0.0f, 5L));
  }

  @Test
  void singleRefusalDoesNotFlag() {
    TeleportRotationTracker tracker = tracker(2, 20);

    assertTrue(tracker.arm(1, 90.0f, 0.0f, 0.0f, 0.0f, 0L));
    assertEquals(REFUSED, tracker.observe(0.0f, 0.0f, 1L));
  }

  @Test
  void refusalWindowExpiresStreak() {
    TeleportRotationTracker tracker = tracker(2, 20);

    assertTrue(tracker.arm(1, 90.0f, 0.0f, 0.0f, 0.0f, 0L));
    assertEquals(REFUSED, tracker.observe(0.0f, 0.0f, 1L));
    assertTrue(tracker.arm(2, 90.0f, 0.0f, 0.0f, 0.0f, 31000L));
    assertEquals(REFUSED, tracker.observe(0.0f, 0.0f, 31001L));
    assertTrue(tracker.arm(3, 90.0f, 0.0f, 0.0f, 0.0f, 31002L));
    assertEquals(FLAGGED, tracker.observe(0.0f, 0.0f, 31003L));
  }

  @Test
  void timeoutByPacketCount() {
    TeleportRotationTracker tracker = tracker(1, 2);

    assertTrue(tracker.arm(1, 90.0f, 0.0f, 0.0f, 0.0f, 0L));
    assertEquals(ADOPTED, tracker.observe(90.0f, 0.0f, 1L));
    assertEquals(PENDING, tracker.observe(90.0f, 0.0f, 2L));
    assertEquals(PENDING, tracker.observe(90.0f, 0.0f, 3L));
    assertEquals(EXPIRED, tracker.observe(90.0f, 0.0f, 4L));
    assertFalse(tracker.armed());
  }

  @Test
  void timeoutByWallClock() {
    TeleportRotationTracker tracker = tracker(1, 20);

    assertTrue(tracker.arm(1, 90.0f, 0.0f, 0.0f, 0.0f, 0L));
    assertEquals(EXPIRED, tracker.observe(90.0f, 0.0f, 6000L));
    assertFalse(tracker.armed());
  }

  @Test
  void thirdViewResetsTheWatch() {
    TeleportRotationTracker tracker = tracker(1, 20);

    assertTrue(tracker.arm(1, 90.0f, 0.0f, 0.0f, 0.0f, 0L));
    assertEquals(EXPIRED, tracker.observe(45.0f, 0.0f, 1L));
    assertFalse(tracker.armed());
    assertEquals(IGNORED, tracker.observe(0.0f, 0.0f, 2L));
  }

  @Test
  void adoptedViewStaysPendingUntilTimeout() {
    TeleportRotationTracker tracker = tracker(1, 5);

    assertTrue(tracker.arm(1, 90.0f, 0.0f, 0.0f, 0.0f, 0L));
    assertEquals(ADOPTED, tracker.observe(90.0f, 0.0f, 1L));
    assertEquals(PENDING, tracker.observe(90.0f, 0.0f, 2L));
    assertEquals(PENDING, tracker.observe(90.0f, 0.0f, 3L));
  }

  @Test
  void refusalAfterAdoptionIsRefusedOnFirstOccurrenceWithSingleRequiredRefusal() {
    TeleportRotationTracker tracker = tracker(1, 20);

    assertTrue(tracker.arm(1, 270.0f, -20.0f, 0.0f, 0.0f, 0L));
    assertEquals(ADOPTED, tracker.observe(270.0f, -20.0f, 1L));
    assertEquals(FLAGGED, tracker.observe(0.0f, 0.0f, 2L));
  }

  @Test
  void resetClearsRefusalStreak() {
    TeleportRotationTracker tracker = tracker(2, 20);

    assertTrue(tracker.arm(1, 90.0f, 0.0f, 0.0f, 0.0f, 0L));
    assertEquals(REFUSED, tracker.observe(0.0f, 0.0f, 1L));

    tracker.reset();

    assertTrue(tracker.arm(2, 90.0f, 0.0f, 0.0f, 0.0f, 2L));
    assertEquals(REFUSED, tracker.observe(0.0f, 0.0f, 3L));
  }

  @Test
  void clearKeepsRefusalStreak() {
    TeleportRotationTracker tracker = tracker(2, 20);

    assertTrue(tracker.arm(1, 90.0f, 0.0f, 0.0f, 0.0f, 0L));
    assertEquals(REFUSED, tracker.observe(0.0f, 0.0f, 1L));

    tracker.clear();

    assertTrue(tracker.arm(2, 90.0f, 0.0f, 0.0f, 0.0f, 2L));
    assertEquals(FLAGGED, tracker.observe(0.0f, 0.0f, 3L));
  }
}
