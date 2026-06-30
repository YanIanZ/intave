package de.jpx3.intave.check.movement.pathfinder;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link BaritoneMovementMath} — the pure geometry that the {@code Pathfinder}
 * heading-lock detector uses to compare a player's transmitted yaw against the heading implied by
 * their horizontal motion. Minecraft's forward look/move vector is {@code (-sin yaw, cos yaw)}, so
 * forward motion {@code (mx, mz)} implies {@code headingYaw = atan2(-mx, mz)}.
 */
final class BaritoneMovementMathTest {
  private static final double EPS = 1.0e-4d;

  @Test
  void headingYawForCardinalForwardMotion() {
    // yaw 0 faces +Z: forward motion is (0, +s)
    assertEquals(0.0d, BaritoneMovementMath.movementHeadingYaw(0.0d, 0.3d), EPS);
    // yaw 90 faces -X: forward motion is (-s, 0)
    assertEquals(90.0d, BaritoneMovementMath.movementHeadingYaw(-0.3d, 0.0d), EPS);
    // yaw -90 faces +X: forward motion is (+s, 0)
    assertEquals(-90.0d, BaritoneMovementMath.movementHeadingYaw(0.3d, 0.0d), EPS);
    // yaw 180 faces -Z: forward motion is (0, -s)
    assertEquals(180.0d, Math.abs(BaritoneMovementMath.movementHeadingYaw(0.0d, -0.3d)), EPS);
  }

  @Test
  void headingResidualIsZeroWhenYawMatchesForwardMotion() {
    // a player facing 45 deg moving forward: motion direction equals look direction → residual 0
    float yaw = 45f;
    double speed = 0.28d;
    double mx = -Math.sin(Math.toRadians(yaw)) * speed;
    double mz = Math.cos(Math.toRadians(yaw)) * speed;
    assertEquals(0.0d, Math.abs(BaritoneMovementMath.headingResidual(yaw, mx, mz)), EPS);
  }

  @Test
  void headingResidualIsLargeWhenStrafingSideways() {
    // facing +Z (yaw 0) but moving along -X (pure strafe) → ~90 deg residual
    double residual = Math.abs(BaritoneMovementMath.headingResidual(0f, -0.3d, 0.0d));
    assertTrue(residual > 80d, "expected large residual for sideways travel, got " + residual);
  }

  @Test
  void signedYawDeltaWrapsShortestArc() {
    assertEquals(2f, BaritoneMovementMath.signedYawDelta(179f, -179f), EPS); // wraps across +/-180
    assertEquals(-2f, BaritoneMovementMath.signedYawDelta(-179f, 179f), EPS);
    assertEquals(10f, BaritoneMovementMath.signedYawDelta(0f, 10f), EPS);
  }

  @Test
  void belowSpeedFloorReturnsNaNSoCallersSkip() {
    // near-zero motion has no meaningful heading
    assertTrue(Double.isNaN(BaritoneMovementMath.headingResidual(0f, 0.0d, 0.0d)));
  }
}
