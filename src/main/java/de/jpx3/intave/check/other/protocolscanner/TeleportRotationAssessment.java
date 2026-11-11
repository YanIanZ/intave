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

import de.jpx3.intave.math.MathHelper;

/**
 * Pure comparison of the rotation a client reported after a server teleport against the
 * rotation the server demanded and the rotation the client held before the teleport.
 * <p>
 * A vanilla client confirms the teleport with the server-set rotation, while a NoRotate-style
 * client restores its previous view in the movement packets after the confirmation.
 */
final class TeleportRotationAssessment {
  private TeleportRotationAssessment() {
  }

  enum Outcome {
    ADOPTED,
    REFUSED,
    INCONCLUSIVE
  }

  static Outcome assess(
    float sentYaw, float sentPitch,
    float previousYaw, float previousPitch,
    float expectedYaw, float expectedPitch,
    float epsilon, float minServerDelta
  ) {
    if (!isFinite(sentYaw) || !isFinite(sentPitch)
      || !isFinite(previousYaw) || !isFinite(previousPitch)
      || !isFinite(expectedYaw) || !isFinite(expectedPitch)
      || !isFinite(epsilon) || !isFinite(minServerDelta)) {
      return Outcome.INCONCLUSIVE;
    }
    if (rotationDistance(previousYaw, previousPitch, expectedYaw, expectedPitch) < minServerDelta) {
      return Outcome.INCONCLUSIVE;
    }
    float toExpected = rotationDistance(sentYaw, sentPitch, expectedYaw, expectedPitch);
    if (toExpected <= epsilon) {
      return Outcome.ADOPTED;
    }
    float toPrevious = rotationDistance(sentYaw, sentPitch, previousYaw, previousPitch);
    if (toPrevious <= epsilon && toExpected > epsilon) {
      return Outcome.REFUSED;
    }
    return Outcome.INCONCLUSIVE;
  }

  static float rotationDistance(float yaw, float pitch, float otherYaw, float otherPitch) {
    return yawDistance(yaw, otherYaw) + (float) Math.abs(pitch - otherPitch);
  }

  static float yawDistance(float yaw, float otherYaw) {
    return MathHelper.distanceInDegrees(yaw, otherYaw);
  }

  private static boolean isFinite(float value) {
    return !Float.isNaN(value) && !Float.isInfinite(value);
  }
}
