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

/**
 * Pure state machine for {@link TeleportRotationRefusal}. Kept free of Bukkit and Intave services
 * so the arm/adopt/refuse/expire transitions can be tested without a server.
 */
final class TeleportRotationTracker {
  enum Observation {
    IGNORED,
    PENDING,
    ADOPTED,
    REFUSED,
    FLAGGED,
    EXPIRED
  }

  private final int requiredRefusals;
  private final int timeoutPackets;
  private final long cooldownMs;
  private final long refusalWindowMs;
  private final float epsilon;
  private final float minServerDelta;

  private boolean armed;
  private boolean adopted;
  private int awaitedPackets;
  private int refusalStreak;
  private long lastFlagMs;
  private long lastRefusalMs;
  private long armedAtMs;
  private int teleportId;
  private float expectedYaw;
  private float expectedPitch;
  private float previousYaw;
  private float previousPitch;
  private float lastServerDelta;
  private float lastClientDelta;

  TeleportRotationTracker(
    int requiredRefusals, int timeoutPackets, long cooldownMs, long refusalWindowMs,
    float epsilon, float minServerDelta
  ) {
    this.requiredRefusals = Math.max(1, requiredRefusals);
    this.timeoutPackets = Math.max(1, timeoutPackets);
    this.cooldownMs = Math.max(0L, cooldownMs);
    this.refusalWindowMs = Math.max(1000L, refusalWindowMs);
    this.epsilon = Math.max(0.01f, epsilon);
    this.minServerDelta = Math.max(0.0f, minServerDelta);
    this.lastFlagMs = -this.cooldownMs;
  }

  boolean arm(
    int teleportId,
    float expectedYaw, float expectedPitch,
    float previousYaw, float previousPitch,
    long nowMs
  ) {
    if (!isFinite(expectedYaw) || !isFinite(expectedPitch)
      || !isFinite(previousYaw) || !isFinite(previousPitch)) {
      return false;
    }
    float serverDelta = TeleportRotationAssessment.rotationDistance(
      previousYaw, previousPitch, expectedYaw, expectedPitch
    );
    if (serverDelta < minServerDelta) {
      return false;
    }
    this.armed = true;
    this.adopted = false;
    this.awaitedPackets = 0;
    this.armedAtMs = nowMs;
    this.teleportId = teleportId;
    this.expectedYaw = expectedYaw;
    this.expectedPitch = expectedPitch;
    this.previousYaw = previousYaw;
    this.previousPitch = previousPitch;
    this.lastServerDelta = serverDelta;
    this.lastClientDelta = 0.0f;
    return true;
  }

  Observation observe(float sentYaw, float sentPitch, long nowMs) {
    if (!armed) {
      return Observation.IGNORED;
    }
    long timeoutMs = (long) timeoutPackets * 250L;
    if (nowMs - armedAtMs > timeoutMs || ++awaitedPackets > timeoutPackets) {
      reset();
      return Observation.EXPIRED;
    }

    TeleportRotationAssessment.Outcome outcome = TeleportRotationAssessment.assess(
      sentYaw, sentPitch,
      previousYaw, previousPitch,
      expectedYaw, expectedPitch,
      epsilon, minServerDelta
    );

    if (adopted) {
      if (outcome == TeleportRotationAssessment.Outcome.REFUSED) {
        return refusal(sentYaw, sentPitch, nowMs);
      }
      if (outcome == TeleportRotationAssessment.Outcome.INCONCLUSIVE) {
        // The client turned to a third view: a legitimate new aim direction, not a refusal.
        reset();
        return Observation.EXPIRED;
      }
      return Observation.PENDING;
    }

    if (outcome == TeleportRotationAssessment.Outcome.ADOPTED) {
      adopted = true;
      awaitedPackets = 0;
      return Observation.ADOPTED;
    }
    if (outcome == TeleportRotationAssessment.Outcome.REFUSED) {
      return refusal(sentYaw, sentPitch, nowMs);
    }
    if (TeleportRotationAssessment.rotationDistance(
      sentYaw, sentPitch, previousYaw, previousPitch
    ) > epsilon) {
      reset();
      return Observation.EXPIRED;
    }
    return Observation.PENDING;
  }

  boolean armed() {
    return armed;
  }

  int teleportId() {
    return teleportId;
  }

  float lastServerDelta() {
    return lastServerDelta;
  }

  float lastClientDelta() {
    return lastClientDelta;
  }

  void clear() {
    armed = false;
    adopted = false;
    awaitedPackets = 0;
  }

  void reset() {
    clear();
    refusalStreak = 0;
  }

  private Observation refusal(float sentYaw, float sentPitch, long nowMs) {
    lastServerDelta = TeleportRotationAssessment.rotationDistance(
      previousYaw, previousPitch, expectedYaw, expectedPitch
    );
    lastClientDelta = TeleportRotationAssessment.rotationDistance(
      sentYaw, sentPitch, previousYaw, previousPitch
    );
    if (nowMs - lastRefusalMs > refusalWindowMs) {
      refusalStreak = 0;
    }
    lastRefusalMs = nowMs;
    refusalStreak++;
    boolean flag = refusalStreak >= requiredRefusals
      && nowMs - lastFlagMs >= cooldownMs;
    if (flag) {
      lastFlagMs = nowMs;
      refusalStreak = 0;
    }
    clear();
    return flag ? Observation.FLAGGED : Observation.REFUSED;
  }

  private static boolean isFinite(float value) {
    return !Float.isNaN(value) && !Float.isInfinite(value);
  }
}
