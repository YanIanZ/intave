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

import de.jpx3.intave.check.other.protocolscanner.TeleportRotationAssessment.Outcome;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class TeleportRotationAssessmentTest {
  private static final float EPSILON = 0.5f;
  private static final float MIN_SERVER_DELTA = 30.0f;

  @Test
  void adoptsExactExpectedRotation() {
    assertEquals(Outcome.ADOPTED, assess(90, 30, 0, 0, 90, 30));
  }

  @Test
  void refusesExactPreviousRotation() {
    assertEquals(Outcome.REFUSED, assess(0, 0, 0, 0, 90, 30));
  }

  @Test
  void detectsPitchOnlyRefusal() {
    // The yaw stays identical, only the server-set pitch differs.
    assertEquals(Outcome.REFUSED, assess(10, 0, 10, 0, 10, 45));
    assertEquals(Outcome.ADOPTED, assess(10, 45, 10, 0, 10, 45));
  }

  @Test
  void handlesYawWrapAround() {
    // 100 to -100 is a 160 degree turn through the wrap; the raw difference is 200.
    assertEquals(Outcome.REFUSED, assess(100, 0, 100, 0, -100, 0));
    assertEquals(Outcome.ADOPTED, assess(-100, 0, 100, 0, -100, 0));
    assertEquals(20.0f, TeleportRotationAssessment.yawDistance(350, 10), 0.0001f);
  }

  @Test
  void ignoresWrapThatDoesNotDemandATurn() {
    assertEquals(Outcome.INCONCLUSIVE, assess(358, 0, 358, 0, 2, 0));
  }

  @Test
  void respectsEpsilonBoundary() {
    // Exactly on the tolerance still counts, just above it does not.
    assertEquals(Outcome.ADOPTED, assess(90.5f, 30, 0, 0, 90, 30));
    assertEquals(Outcome.INCONCLUSIVE, assess(90.51f, 30, 0, 0, 90, 30));

    assertEquals(Outcome.REFUSED, assess(0.5f, 0, 0, 0, 90, 30));
    assertEquals(Outcome.INCONCLUSIVE, assess(0.51f, 0, 0, 0, 90, 30));
  }

  @Test
  void respectsMinServerDeltaBoundary() {
    assertEquals(Outcome.ADOPTED, assess(30, 0, 0, 0, 30, 0));
    assertEquals(Outcome.REFUSED, assess(0, 0, 0, 0, 30, 0));
    assertEquals(Outcome.INCONCLUSIVE, assess(0, 0, 0, 0, 29.99f, 0));
    assertEquals(Outcome.INCONCLUSIVE, assess(29.99f, 0, 0, 0, 29.99f, 0));
  }

  @Test
  void ignoresLegitimateMouseMovement() {
    assertEquals(Outcome.INCONCLUSIVE, assess(45, 15, 0, 0, 90, 30));
  }

  @Test
  void ignoresNonFiniteValues() {
    assertEquals(Outcome.INCONCLUSIVE, assess(Float.NaN, 30, 0, 0, 90, 30));
    assertEquals(Outcome.INCONCLUSIVE, assess(90, Float.NaN, 0, 0, 90, 30));
    assertEquals(Outcome.INCONCLUSIVE, assess(90, 30, Float.NaN, 0, 90, 30));
    assertEquals(Outcome.INCONCLUSIVE, assess(90, 30, 0, Float.POSITIVE_INFINITY, 90, 30));
    assertEquals(Outcome.INCONCLUSIVE, assess(90, 30, 0, 0, Float.NEGATIVE_INFINITY, 30));
    assertEquals(Outcome.INCONCLUSIVE, assess(90, 30, 0, 0, 90, Float.NaN));
    assertEquals(Outcome.INCONCLUSIVE, assess(90, 30, 0, 0, 90, 30, Float.NaN, MIN_SERVER_DELTA));
  }

  @Test
  void ignoresTeleportWithoutARotationChange() {
    assertEquals(Outcome.INCONCLUSIVE, assess(10, 10, 10, 10, 10, 10));
  }

  private static Outcome assess(
    float sentYaw, float sentPitch,
    float previousYaw, float previousPitch,
    float expectedYaw, float expectedPitch
  ) {
    return assess(sentYaw, sentPitch, previousYaw, previousPitch, expectedYaw, expectedPitch,
      EPSILON, MIN_SERVER_DELTA);
  }

  private static Outcome assess(
    float sentYaw, float sentPitch,
    float previousYaw, float previousPitch,
    float expectedYaw, float expectedPitch,
    float epsilon, float minServerDelta
  ) {
    return TeleportRotationAssessment.assess(
      sentYaw, sentPitch,
      previousYaw, previousPitch,
      expectedYaw, expectedPitch,
      epsilon, minServerDelta
    );
  }
}
