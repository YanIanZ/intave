package de.jpx3.intave.check.movement.pathfinder;

/**
 * Pure geometry helpers for the {@code Pathfinder} (Baritone) detection.
 *
 * <p>Minecraft's forward look/move vector for a yaw is {@code (-sin yaw, cos yaw)} (see
 * {@code MovementMetadata#vectorForRotation}). A pathfinding bot such as Baritone runs with
 * {@code antiCheatCompatibility} enabled by default, which keeps the transmitted yaw locked to the
 * direction it is travelling so it never sprints sideways. That makes the angular residual between
 * the transmitted yaw and the heading implied by the horizontal motion stay near zero even while the
 * bot is turning along a path — something a human, who decouples view from travel, does not sustain.
 *
 * <p>These helpers are intentionally side-effect free and contain no Bukkit/packet dependency, so the
 * detector logic that builds on them can be unit-tested directly.
 */
public final class BaritoneMovementMath {
  /** Below this horizontal speed (blocks/tick) the motion direction is too noisy to imply a heading. */
  public static final double MIN_SPEED = 0.05d;

  private BaritoneMovementMath() {
  }

  /**
   * The yaw (degrees) that a player walking <i>forward</i> with the given horizontal motion would be
   * facing. Inverse of the forward look vector {@code (-sin yaw, cos yaw)}: {@code atan2(-mx, mz)}.
   */
  public static double movementHeadingYaw(double motionX, double motionZ) {
    return Math.toDegrees(Math.atan2(-motionX, motionZ));
  }

  /**
   * Signed shortest-arc difference between the transmitted yaw and the forward-motion heading, in
   * degrees within {@code [-180, 180)}. Returns {@link Double#NaN} when the motion is below
   * {@link #MIN_SPEED} (no meaningful heading), so callers skip the sample.
   */
  public static double headingResidual(float yaw, double motionX, double motionZ) {
    double speed = Math.sqrt(motionX * motionX + motionZ * motionZ);
    if (speed < MIN_SPEED) {
      return Double.NaN;
    }
    double headingYaw = movementHeadingYaw(motionX, motionZ);
    return signedYawDelta((float) headingYaw, yaw);
  }

  /** Signed shortest-arc yaw delta {@code to - from} in degrees, wrapped to {@code [-180, 180)}. */
  public static float signedYawDelta(float from, float to) {
    float delta = (to - from) % 360f;
    if (delta >= 180f) {
      delta -= 360f;
    } else if (delta < -180f) {
      delta += 360f;
    }
    return delta;
  }
}
