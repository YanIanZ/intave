package de.jpx3.intave.check.combat.heuristics.combatpatterns.rotation;

import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.check.combat.heuristics.ClassicHeuristic;
import de.jpx3.intave.check.combat.heuristics.ConfidenceBuffer;
import de.jpx3.intave.check.combat.heuristics.HeuristicsClassicType;
import de.jpx3.intave.check.combat.heuristics.RollingCorrelation;
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
 * Detects aimbots that interpolate toward a target along a <i>straight line in angle space</i>.
 *
 * <p>This is the two-dimensional counterpart to {@link RotationConstantSpeedHeuristic} (constant turn
 * <i>speed</i>) and {@link AimSmoothingHeuristic} (constant ease <i>ratio</i>): a linear-interpolation
 * aimbot moves a fixed fraction of the remaining angle each tick, so every per-tick rotation step
 * {@code (Δyaw, Δpitch)} points in almost the same direction and the steps are <i>collinear</i> — they
 * trace a straight line. A human tracking a target produces scattered, independently-jittering yaw and
 * pitch corrections, so their steps are not collinear.
 *
 * <p>While the player is actively fighting a moving target, each tick that genuinely rotates on
 * <i>both</i> axes contributes a {@code (Δyaw, Δpitch)} pair (a near-zero axis would make the linear
 * fit degenerate, so those ticks are skipped and break the path). Over a window of {@link #WINDOW}
 * such pairs the {@linkplain RollingCorrelation#correlation() Pearson correlation} of the path is
 * measured; a magnitude above {@link #CORRELATION_THRESHOLD} means the rotation moved along a
 * robotically straight line.
 *
 * <p>Evidence is gathered in a decaying {@link ConfidenceBuffer} and it flags with a
 * {@linkplain ClassicHeuristic#flag(Player, String, double) graded confidence}. Like the other
 * behavioural rotation tells it ships at violation level {@code 0} (see
 * {@code heuristics.classic.rotation-linearity}) so out of the box it only feeds cross-heuristic
 * corroboration and verbose output; raise it after confirming no false positives.
 */
public final class RotationLinearityHeuristic extends ClassicHeuristic<RotationLinearityHeuristic.LinearityMeta> {
  /** Both axes must rotate at least this much (degrees) for a tick to count as a genuine 2D step. */
  private static final float MIN_AXIS_DELTA = 0.6f;
  /** Number of consecutive 2D rotation steps evaluated per window. */
  private static final int WINDOW = 10;
  /** Correlation magnitude above which the rotation path is considered robotically straight. */
  private static final double CORRELATION_THRESHOLD = 0.996d;
  private static final double BUFFER_HALF_LIFE_MILLIS = 5_000d;
  /** Accumulated straight-path evidence required before a flag is released. */
  private static final double RELEASE_THRESHOLD = 3.0d;

  public RotationLinearityHeuristic(Heuristics parentCheck) {
    super(parentCheck, HeuristicsClassicType.ROTATION_LINEARITY, LinearityMeta.class);
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
    LinearityMeta heuristicMeta = metaOf(user);

    if (entity == null
      || movementData.ticksPast(TELEPORT) < 20
      || !attackData.recentlyAttacked(1000)
      || !entity.moving(0.05)) {
      heuristicMeta.path.reset();
      return;
    }

    float deltaYaw = wrapDegrees(movementData.rotationYaw - movementData.lastRotationYaw);
    float deltaPitch = movementData.rotationPitch - movementData.lastRotationPitch;

    // Need genuine rotation on *both* axes; a near-zero axis makes the linear fit degenerate. A short
    // still gap (e.g. an injected short-stop pause) skips the degenerate sample but does not discard
    // the window; only the per-engagement gate above clears it.
    if (Math.abs(deltaYaw) < MIN_AXIS_DELTA || Math.abs(deltaPitch) < MIN_AXIS_DELTA) {
      return;
    }

    heuristicMeta.path.accept(deltaYaw, deltaPitch);
    if (heuristicMeta.path.count() < WINDOW) {
      return;
    }

    double correlation = Math.abs(heuristicMeta.path.correlation());
    heuristicMeta.path.reset();

    long now = System.currentTimeMillis();
    if (correlation > CORRELATION_THRESHOLD) {
      heuristicMeta.evidence.add(1.0d, now);
      if (heuristicMeta.evidence.consumeIfAtLeast(RELEASE_THRESHOLD, now)) {
        double confidence = MathHelper.minmax(0.3d,
          (correlation - CORRELATION_THRESHOLD) / (1.0d - CORRELATION_THRESHOLD), 1.0d);
        flag(player, "linear aim path (|r| " + MathHelper.formatDouble(correlation, 4) + ")", confidence);
      }
    } else {
      // not a straight path this window — let the accumulated evidence decay
      heuristicMeta.evidence.value(now);
    }
  }

  /** Wraps a degree delta to {@code [-180, 180)} so yaw wrap-around does not distort the step. */
  private static float wrapDegrees(float degrees) {
    float wrapped = degrees % 360.0f;
    if (wrapped >= 180.0f) {
      wrapped -= 360.0f;
    } else if (wrapped < -180.0f) {
      wrapped += 360.0f;
    }
    return wrapped;
  }

  public static final class LinearityMeta extends CheckCustomMetadata {
    private final RollingCorrelation path = new RollingCorrelation();
    private final ConfidenceBuffer evidence = new ConfidenceBuffer(BUFFER_HALF_LIFE_MILLIS);
  }
}
