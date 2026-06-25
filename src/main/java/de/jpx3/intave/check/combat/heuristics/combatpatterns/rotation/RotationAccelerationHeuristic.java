package de.jpx3.intave.check.combat.heuristics.combatpatterns.rotation;

import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.check.combat.heuristics.ClassicHeuristic;
import de.jpx3.intave.check.combat.heuristics.ConfidenceBuffer;
import de.jpx3.intave.check.combat.heuristics.HeuristicsClassicType;
import de.jpx3.intave.check.combat.heuristics.RollingStatistics;
import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.tracker.entity.Entity;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.AttackMetadata;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.MetadataBundle;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.entity.Player;

import static de.jpx3.intave.check.movement.physics.MoveMetric.TELEPORT;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.LOOK;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.POSITION_LOOK;

/**
 * Detects aim-assist that sweeps at a robotically <i>constant angular acceleration</i> — a steady
 * speed ramp with near-zero jerk.
 *
 * <p>This is a distinct fingerprint to its siblings. {@link RotationConstantSpeedHeuristic} catches a
 * <i>constant velocity</i> (zero acceleration); {@link AimSmoothingHeuristic} catches a constant
 * <i>geometric</i> ease ratio (each step a fixed fraction of the last). An aimbot that interpolates
 * linearly instead ramps its turn speed by a near-constant amount every tick — the velocity is not
 * constant (so constant-speed misses it) and the step-to-step ratio is not constant (so smoothing
 * misses it), but the <i>acceleration</i> is. A human turn never holds a steady acceleration for long:
 * acquisition, overshoot and micro-corrections make the per-tick acceleration jitter and change sign.
 *
 * <p>While the player is actively fighting a moving target, each pair of consecutive genuine turning
 * ticks yields one signed acceleration sample (this tick's yaw speed minus the last). Over a window of
 * {@link #WINDOW} contiguous turning ticks the acceleration's {@linkplain
 * RollingStatistics#standardDeviation() standard deviation} is measured; a value below
 * {@link #ACCEL_STDDEV_THRESHOLD}, while the mean acceleration magnitude is a non-trivial ramp (above
 * {@link #MIN_MEAN_ABS_ACCEL}, so a near-zero — i.e. constant-speed — sweep is left to its own
 * heuristic), means a robotically constant ramp. Any still or non-turning tick breaks the sweep and
 * clears the partial window, so only an uninterrupted ramp is judged.
 *
 * <p>Evidence is gathered in a decaying {@link ConfidenceBuffer} so a single uniform burst fades, and
 * it flags with a {@linkplain ClassicHeuristic#flag(Player, String, double) graded confidence} that
 * scales with how uniform the acceleration was. Like the other behavioural rotation tells it ships at
 * violation level {@code 0} (see {@code heuristics.classic.rotation-acceleration}) so out of the box it
 * only feeds cross-heuristic corroboration and verbose output; raise it after confirming no false
 * positives.
 */
public final class RotationAccelerationHeuristic extends ClassicHeuristic<RotationAccelerationHeuristic.AccelerationMeta> {
  /** Only ticks turning at least this fast (degrees) are sampled, so idle aim is ignored. */
  private static final float MIN_TURN_SPEED = 2.0f;
  /** Number of consecutive acceleration samples evaluated per window. */
  private static final int WINDOW = 8;
  /** Acceleration std-dev (deg/tick²) below which the ramp is considered robotically constant. */
  private static final double ACCEL_STDDEV_THRESHOLD = 0.25d;
  /** Mean acceleration magnitude must exceed this — a near-zero ramp is just constant speed. */
  private static final double MIN_MEAN_ABS_ACCEL = 0.6d;
  private static final double BUFFER_HALF_LIFE_MILLIS = 5_000d;
  /** Accumulated robotic-ramp evidence required before a flag is released. */
  private static final double RELEASE_THRESHOLD = 3.0d;

  public RotationAccelerationHeuristic(Heuristics parentCheck) {
    super(parentCheck, HeuristicsClassicType.ROTATION_ACCELERATION, AccelerationMeta.class);
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsIn = {LOOK, POSITION_LOOK}
  )
  public void receiveMovement(PacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    MetadataBundle meta = user.meta();
    MovementMetadata movementData = meta.movement();
    AttackMetadata attackData = meta.attack();
    Entity entity = attackData.lastAttackedEntity();

    if (entity == null
      || movementData.ticksPast(TELEPORT) < 20
      || !attackData.recentlyAttacked(1000)
      || !entity.moving(0.05)) {
      return;
    }

    float yawSpeed = MathHelper.distanceInDegrees(movementData.rotationYaw, movementData.lastRotationYaw);
    AccelerationMeta heuristicMeta = metaOf(user);
    float previous = heuristicMeta.lastYawSpeed;
    heuristicMeta.lastYawSpeed = yawSpeed;

    // A still / non-turning tick breaks the ramp; only contiguous turning steps form accelerations.
    if (yawSpeed < MIN_TURN_SPEED || previous < MIN_TURN_SPEED) {
      heuristicMeta.accelerations.reset();
      return;
    }

    double acceleration = yawSpeed - previous; // signed deg/tick change in angular velocity
    heuristicMeta.accelerations.accept(acceleration);
    if (heuristicMeta.accelerations.count() < WINDOW) {
      return;
    }

    double standardDeviation = heuristicMeta.accelerations.standardDeviation();
    double meanMagnitude = Math.abs(heuristicMeta.accelerations.mean());
    heuristicMeta.accelerations.reset();

    long now = System.currentTimeMillis();
    if (standardDeviation < ACCEL_STDDEV_THRESHOLD && meanMagnitude > MIN_MEAN_ABS_ACCEL) {
      heuristicMeta.evidence.add(1.0d, now);
      if (heuristicMeta.evidence.consumeIfAtLeast(RELEASE_THRESHOLD, now)) {
        double confidence = MathHelper.minmax(0.3d,
          (ACCEL_STDDEV_THRESHOLD - standardDeviation) / ACCEL_STDDEV_THRESHOLD, 1.0d);
        flag(player, "robotic constant aim acceleration (sd "
          + MathHelper.formatDouble(standardDeviation, 3) + " deg/t², ramp "
          + MathHelper.formatDouble(meanMagnitude, 2) + " deg/t)", confidence);
      }
    } else {
      // not a constant-acceleration ramp this window — let the accumulated evidence decay
      heuristicMeta.evidence.value(now);
    }
  }

  public static final class AccelerationMeta extends CheckCustomMetadata {
    private float lastYawSpeed;
    private final RollingStatistics accelerations = new RollingStatistics();
    private final ConfidenceBuffer evidence = new ConfidenceBuffer(BUFFER_HALF_LIFE_MILLIS);
  }
}
